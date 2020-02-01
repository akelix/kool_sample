package de.fabmax.kool.scene

import de.fabmax.kool.KoolContext
import de.fabmax.kool.math.Mat4f
import de.fabmax.kool.math.MutableVec3f
import de.fabmax.kool.math.Vec3f
import de.fabmax.kool.pipeline.Attribute
import de.fabmax.kool.pipeline.GlslType
import de.fabmax.kool.util.BoundingBox
import de.fabmax.kool.util.Float32Buffer
import de.fabmax.kool.util.createFloat32Buffer
import de.fabmax.kool.util.logW
import kotlin.math.min

open class InstancedMesh(meshData: MeshData, name: String? = null,
                         var instances: Instances<*> = identityInstance(),
                         val attributes: List<Attribute> = MODEL_INSTANCES) :
        Mesh(meshData, name) {

//    private var instanceBuffer: BufferResource? = null
//    private val instanceBinders = mutableMapOf<Attribute, VboBinder>()
    private val instanceStride: Int

    /**
     * Estimated [BoundingBox] containing all instances. Only valid if [isFrustumChecked] is true and individual
     * instance model matrices don't apply scaling.
     */
    override val bounds: BoundingBox
        get() = instances.bounds

    init {
        // todo: standard ray test doesn't work for instanced meshes
        rayTest = MeshRayTest.nopTest()

        // compute instance stride as size of all instance attributes
        // stride is in bytes, attribute type size is in elements with 4 bytes each
        instanceStride = attributes.sumBy { it.type.size }

        attributes.filter { it.divisor == 0 }.forEach { logW { "InstancedMesh attribute ${it.name} has divisor = 0" } }
    }

    override fun preRender(ctx: KoolContext) {
        super.preRender(ctx)
        if (isVisible) {
            instances.setupInstances(this, ctx)
        }
    }

    override fun render(ctx: KoolContext) {
//        if (instanceBuffer == null) {
//            // create buffer object with instance data
//            instanceBuffer = BufferResource.create(GL_ARRAY_BUFFER, ctx)
//
//            var pos  = 0
//            attributes.forEach {
//                instanceBinders[it] = VboBinder(instanceBuffer!!, it.type.size, instanceStride, pos, GL_FLOAT)
//                pos += it.type.size
//            }
//        }
//        // bind instance data buffer
//        instances.instanceData?.let {
//            instanceBuffer!!.setData(it, GL_DYNAMIC_DRAW, ctx)
//        }
//
//        // disable frustum check flag before calling render, standard frustum check doesn't work with InstancedMesh
//        val wasFrustumChecked = isFrustumChecked
//        isFrustumChecked = false
//        super.render(ctx)
//        isFrustumChecked = wasFrustumChecked
    }

    override fun dispose(ctx: KoolContext) {
//        super.dispose(ctx)
//        instanceBuffer?.delete(ctx)
//        instanceBuffer = null
//        instanceBinders.clear()
    }

    open class Instance(val modelMat: Mat4f) {
        /**
         * Radius of a single instance in global coordinates. Is used for frustum culling of individual instances.
         * A good starting point is distance between center and max of corresponding MeshData bounds. Larger values
         * might be needed for correct shadow casting etc.
         */
        var radius = 1f

        open fun putInstanceAttributes(target: Float32Buffer) {
            target.put(modelMat.matrix)
        }

        open fun getLocalOrigin(result: MutableVec3f): MutableVec3f {
            return modelMat.transform(result.set(Vec3f.ZERO))
        }
    }

    open class Instances<T: Instance>(maxInstances: Int) {
        val instances = mutableListOf<T>()
        val bounds = BoundingBox()
        var maxInstances = maxInstances
            set(value) {
                field = value
                instanceData = null
            }

        var instanceData: Float32Buffer? = null
            private set
        var numInstances = 0
            private set

        private val tmpVec = MutableVec3f()

        open fun clearInstances() {
            instances.clear()
        }

        fun addInstance(instance: T) {
            instances += instance
        }

        fun addInstances(instance: List<T>) {
            instances += instance
        }

        operator fun plusAssign(instance: T) = addInstance(instance)

        fun setupInstances(mesh: InstancedMesh, ctx: KoolContext) {
            val data = instanceData ?: createFloat32Buffer(maxInstances * mesh.instanceStride).also {
                instanceData = it
            }

            data.clear()
            bounds.clear()
            numInstances = 0

            putInstanceData(data, mesh, ctx)
        }

        protected open fun putInstanceData(target: Float32Buffer, mesh: InstancedMesh, ctx: KoolContext) {
            val cam = mesh.scene?.camera
            if (mesh.isFrustumChecked && cam != null) {
                for (i in instances.indices) {
                    // determine local instance center position
                    instances[i].getLocalOrigin(tmpVec)
                    bounds.add(tmpVec)

                    if (isIncludeInstance(instances[i], tmpVec, cam, ctx)) {
                        putInstance(instances[i], target)
                    }
                    if (numInstances == maxInstances) {
                        // maximum buffer size reached
                        break
                    }
                }

                if (!bounds.isEmpty) {
                    tmpVec.set(mesh.meshData.bounds.size).scale(0.5f)
                    bounds.expand(tmpVec)
                }
            } else {
                for (i in 0 until min(instances.size, maxInstances)) {
                    putInstance(instances[i], target)
                }
            }
        }

        protected open fun isIncludeInstance(instance: T, localPos: Vec3f, cam: Camera, ctx: KoolContext): Boolean {
            // determine global instance center position
            ctx.mvpState.modelMatrix.transform(tmpVec)
            return cam.isInFrustum(tmpVec, instance.radius)
        }

        protected open fun putInstance(instance: T, target: Float32Buffer) {
            if (numInstances < maxInstances) {
                instance.putInstanceAttributes(target)
                numInstances++
            } else {
                logW { "Discarding instance: max instance count reached" }
            }
        }
    }

    class SimpleInstances(maxInstances: Int) : Instances<Instance>(maxInstances) {
        fun addInstance(modelMat: Mat4f) {
            instances += Instance(modelMat)
        }
    }

    companion object {
        val MODEL_INSTANCES_0 = Attribute("attrib_model_insts_0", GlslType.VEC_4F).apply {
            glslSrcName = "attrib_model_insts"
            locationOffset = 0
            divisor = 1
        }
        val MODEL_INSTANCES_1 = Attribute("attrib_model_insts_1", GlslType.VEC_4F).apply {
            glslSrcName = "attrib_model_insts"
            locationOffset = 1
            divisor = 1
        }
        val MODEL_INSTANCES_2 = Attribute("attrib_model_insts_2", GlslType.VEC_4F).apply {
            glslSrcName = "attrib_model_insts"
            locationOffset = 2
            divisor = 1
        }
        val MODEL_INSTANCES_3 = Attribute("attrib_model_insts_3", GlslType.VEC_4F).apply {
            glslSrcName = "attrib_model_insts"
            locationOffset = 3
            divisor = 1
        }
        val MODEL_INSTANCES = listOf(MODEL_INSTANCES_0, MODEL_INSTANCES_1, MODEL_INSTANCES_2, MODEL_INSTANCES_3)

        fun makeAttributeList(vararg customAttribs: Attribute): List<Attribute> {
            val attribs = mutableListOf(MODEL_INSTANCES_0, MODEL_INSTANCES_1, MODEL_INSTANCES_2, MODEL_INSTANCES_3)
            attribs.addAll(customAttribs)
            return attribs
        }

        fun identityInstance(): SimpleInstances {
            return SimpleInstances(1).apply {
                addInstance(Mat4f().setIdentity())
            }
        }
    }
}
