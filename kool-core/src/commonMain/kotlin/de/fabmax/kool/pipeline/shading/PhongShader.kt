package de.fabmax.kool.pipeline.shading

import de.fabmax.kool.KoolContext
import de.fabmax.kool.pipeline.*
import de.fabmax.kool.pipeline.shadermodel.*
import de.fabmax.kool.scene.Mesh
import de.fabmax.kool.util.CascadedShadowMap
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.ShadowMap
import de.fabmax.kool.util.SimpleShadowMap

fun phongShader(cfgBlock: PhongShader.PhongConfig.() -> Unit): PhongShader {
    val cfg = PhongShader.PhongConfig()
    cfg.cfgBlock()
    return PhongShader(cfg)
}

class PhongShader(cfg: PhongConfig, model: ShaderModel = defaultPhongModel(cfg)) : ModeledShader(model) {

    private val cullMethod = cfg.cullMethod
    private val isBlending = cfg.alphaMode is AlphaModeBlend
    private val shadowMaps = Array(cfg.shadowMaps.size) { cfg.shadowMaps[it] }
    private val isReceivingShadow = cfg.shadowMaps.isNotEmpty()

    private var uShininess: PushConstantNode1f? = null
    private var uSpecularIntensity: PushConstantNode1f? = null

    private var albedoSampler: TextureSampler? = null
    private var normalSampler: TextureSampler? = null
    private var uAlbedo: PushConstantNodeColor? = null

    var shininess = cfg.shininess
        set(value) {
            field = value
            uShininess?.uniform?.value = value
        }
    var specularIntensity = cfg.specularIntensity
        set(value) {
            field = value
            uSpecularIntensity?.uniform?.value = value
        }

    var albedo: Color = cfg.albedo
        set(value) {
            field = value
            uAlbedo?.uniform?.value?.set(value)
        }
    var albedoMap: Texture? = cfg.albedoMap
        set(value) {
            field = value
            albedoSampler?.texture = value
        }
    var normalMap: Texture? = cfg.normalMap
        set(value) {
            field = value
            normalSampler?.texture = value
        }

    private val depthSamplers = Array<TextureSampler?>(shadowMaps.size) { null }

    override fun onPipelineSetup(builder: Pipeline.Builder, mesh: Mesh, ctx: KoolContext) {
        builder.cullMethod = cullMethod
        builder.blendMode = if (isBlending) BlendMode.BLEND_PREMULTIPLIED_ALPHA else BlendMode.DISABLED
        super.onPipelineSetup(builder, mesh, ctx)
    }

    override fun onPipelineCreated(pipeline: Pipeline, mesh: Mesh, ctx: KoolContext) {
        uShininess = model.findNode("uShininess")
        uShininess?.uniform?.value = shininess
        uSpecularIntensity = model.findNode("uSpecularIntensity")
        uSpecularIntensity?.uniform?.value = specularIntensity

        uAlbedo = model.findNode("uAlbedo")
        uAlbedo?.uniform?.value?.set(albedo)

        albedoSampler = model.findNode<TextureNode>("tAlbedo")?.sampler
        albedoSampler?.let { it.texture = albedoMap }
        normalSampler = model.findNode<TextureNode>("tNormal")?.sampler
        normalSampler?.let { it.texture = normalMap }

        if (isReceivingShadow) {
            for (i in depthSamplers.indices) {
                val sampler = model.findNode<TextureNode>("depthMap_$i")?.sampler
                depthSamplers[i] = sampler
                shadowMaps[i].setupSampler(sampler)
            }
        }

        super.onPipelineCreated(pipeline, mesh, ctx)
    }

    companion object {
        fun defaultPhongModel(cfg: PhongConfig) = ShaderModel("defaultPhongModel()").apply {
            val ifNormals: StageInterfaceNode
            val ifColors: StageInterfaceNode?
            val ifTexCoords: StageInterfaceNode?
            val ifTangents: StageInterfaceNode?
            val ifFragPos: StageInterfaceNode
            val mvpNode: UniformBufferMvp
            val shadowMapNodes = mutableListOf<ShadowMapNode>()

            vertexStage {
                val modelMat: ShaderNodeIoVar
                val mvpMat: ShaderNodeIoVar

                mvpNode = mvpNode()
                if (cfg.isInstanced) {
                    modelMat = multiplyNode(mvpNode.outModelMat, instanceAttrModelMat().output).output
                    mvpMat = multiplyNode(mvpNode.outMvpMat, instanceAttrModelMat().output).output
                } else {
                    modelMat = mvpNode.outModelMat
                    mvpMat = mvpNode.outMvpMat
                }

                ifColors = if (cfg.albedoSource == Albedo.VERTEX_ALBEDO) {
                    stageInterfaceNode("ifColors", attrColors().output)
                } else {
                    null
                }
                ifTexCoords = if (cfg.requiresTexCoords()) {
                    stageInterfaceNode("ifTexCoords", attrTexCoords().output)
                } else {
                    null
                }
                ifTangents = if (cfg.isNormalMapped) {
                    val tanAttr = attrTangents().output
                    val tan = vec3TransformNode(splitNode(tanAttr, "xyz").output, modelMat, 0f)
                    val tan4 = combineXyzWNode(tan.outVec3, splitNode(tanAttr, "w").output)
                    stageInterfaceNode("ifTangents", tan4.output)
                } else {
                    null
                }
                val nrm = vec3TransformNode(attrNormals().output, modelMat, 0f)
                ifNormals = stageInterfaceNode("ifNormals", nrm.outVec3)

                val worldPos = vec3TransformNode(attrPositions().output, modelMat, 1f).outVec3
                ifFragPos = stageInterfaceNode("ifFragPos", worldPos)

                val viewPos = vec4TransformNode(worldPos, mvpNode.outViewMat).outVec4

                cfg.shadowMaps.forEachIndexed { i, map ->
                    when (map) {
                        is CascadedShadowMap -> shadowMapNodes += cascadedShadowMapNode(map, "depthMap_$i", viewPos, worldPos)
                        is SimpleShadowMap -> shadowMapNodes += simpleShadowMapNode(map, "depthMap_$i", worldPos)
                    }
                }
                positionOutput = vec4TransformNode(worldPos, mvpMat).outVec4
            }
            fragmentStage {
                val mvpFrag = mvpNode.addToStage(fragmentStageGraph)
                val lightNode = multiLightNode(cfg.maxLights)
                shadowMapNodes.forEach {
                    lightNode.inShaodwFacs[it.lightIndex] = it.outShadowFac
                }

                val albedo = when (cfg.albedoSource) {
                    Albedo.VERTEX_ALBEDO -> ifColors!!.output
                    Albedo.STATIC_ALBEDO -> pushConstantNodeColor("uAlbedo").output
                    Albedo.TEXTURE_ALBEDO -> {
                        textureSamplerNode(textureNode("tAlbedo"), ifTexCoords!!.output, false).outColor
                    }
                    Albedo.CUBE_MAP_ALBEDO -> throw IllegalStateException("CUBE_MAP_ALBEDO is not allowed for PbrShader")
                }

                val normal = if (cfg.isNormalMapped && ifTangents != null) {
                    val bumpNormal = normalMapNode(textureNode("tNormal"), ifTexCoords!!.output, ifNormals.output, ifTangents.output)
                    bumpNormal.outNormal
                } else {
                    ifNormals.output
                }

                val phongMat = phongMaterialNode(albedo, normal, ifFragPos.output, mvpFrag.outCamPos, lightNode).apply {
                    lightBacksides = cfg.lightBacksides
                    inShininess = pushConstantNode1f("uShininess").output
                    inSpecularIntensity = pushConstantNode1f("uSpecularIntensity").output
                }
                colorOutput(phongMat.outColor)
            }
        }
    }

    class PhongConfig {
        var albedoSource = Albedo.VERTEX_ALBEDO
        var isNormalMapped = false

        // initial shader values
        var albedo = Color.GRAY
        var shininess = 20f
        var specularIntensity = 1f

        var maxLights = 4
        val shadowMaps = mutableListOf<ShadowMap>()
        var lightBacksides = false

        var cullMethod = CullMethod.CULL_BACK_FACES
        var alphaMode: AlphaMode = AlphaModeOpaque()

        var isInstanced = false

        var albedoMap: Texture? = null
        var normalMap: Texture? = null

        fun useAlbedoMap(albedoMap: String) =
                useAlbedoMap(Texture(albedoMap))

        fun useAlbedoMap(albedoMap: Texture?) {
            this.albedoMap = albedoMap
            albedoSource = Albedo.TEXTURE_ALBEDO
        }

        fun useNormalMap(normalMap: String) =
                useNormalMap(Texture(normalMap))

        fun useNormalMap(normalMap: Texture?) {
            this.normalMap = normalMap
            isNormalMapped = true
        }

        fun requiresTexCoords(): Boolean {
            return albedoSource == Albedo.TEXTURE_ALBEDO || isNormalMapped
        }
    }
}
