package de.fabmax.kool.demo.fluidsim

import de.fabmax.kool.KoolContext
import de.fabmax.kool.demo.*
import de.fabmax.kool.demo.menu.DemoMenu
import de.fabmax.kool.math.*
import de.fabmax.kool.modules.ksl.KslShader
import de.fabmax.kool.modules.ksl.KslUnlitShader
import de.fabmax.kool.modules.ksl.lang.*
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.pipeline.*
import de.fabmax.kool.scene.Mesh
import de.fabmax.kool.scene.Scene
import de.fabmax.kool.scene.TrsTransformF
import de.fabmax.kool.scene.addTextureMesh
import de.fabmax.kool.toString
import de.fabmax.kool.util.ColorGradient
import de.fabmax.kool.util.MdColor
import kotlin.math.roundToInt
import kotlin.math.sin

class FluidDemo : DemoScene("Fluid Simulation") {

    private val simHeight = 256
    private val simWidth = 454

    private val uStateA = StorageTexture2d(simWidth, simHeight, TexFormat.R_I32, texProps)
    private val vStateA = StorageTexture2d(simWidth, simHeight, TexFormat.R_I32, texProps)
    private val uStateB = StorageTexture2d(simWidth, simHeight, TexFormat.R_I32, texProps)
    private val vStateB = StorageTexture2d(simWidth, simHeight, TexFormat.R_I32, texProps)
    private val smokeDensityA = StorageTexture2d(simWidth, simHeight, TexFormat.R_I32, texProps)
    private val smokeDensityB = StorageTexture2d(simWidth, simHeight, TexFormat.R_I32, texProps)
    private val borderState = StorageTexture2d(simWidth, simHeight, TexFormat.R_I32, texProps)
    private val draw = StorageTexture2d(simWidth, simHeight, TexFormat.RGBA_F32, texPropsDraw)

    private val solvers = mutableListOf<IncompressibilitySolverShader>()
    private val advectionShader = AdvectionShader(uStateA, vStateA, smokeDensityA, uStateB, vStateB, smokeDensityB, borderState)
    private val copyShader = CopyStateShader(
        uStateB, vStateB, smokeDensityB,
        uStateA, vStateA, smokeDensityA,
        borderState,
        draw
    )

    private val solverPass = ComputeRenderPass("Solver", simWidth, simHeight)
    private val advectionPass = ComputeRenderPass(advectionShader, simWidth, simHeight)
    private val copyPass = ComputeRenderPass(copyShader, simWidth, simHeight)

    private val outputShader = OutputShader()
    private val obstacleMesh = Mesh(Attribute.POSITIONS)

    private val isPaused = mutableStateOf(false)
    private var clearCount = 2
    private val isSingleStep = mutableStateOf(false)
    private val movingObstacle = mutableStateOf(false)

    private val numIterations = mutableStateOf(60).onChange {
        solverPass.tasks.forEachIndexed { i, task ->
            task.isEnabled = i < it
        }
    }

    private val isDrawSmoke = mutableStateOf(outputShader.mode == 1).onChange {
        outputShader.mode = if (it) 1 else 0
    }

    private val advectionStep = mutableStateOf(advectionShader.advectionStep).onChange {
        advectionShader.advectionStep = it
    }

    private val overRelaxation = mutableStateOf(1.9f).onChange { value ->
        solvers.forEach { it.overRelaxation = value }
    }

    override fun Scene.setupMainScene(ctx: KoolContext) {
        copyShader.obstaclePos = Vec2f(simHeight / 4f, simHeight / 2f)
        initializeSolverTasks()

        // compute shaders are executed by ComputeRenderPasses, which are added as a scene offscreen pass
        addOffscreenPass(copyPass)
        addOffscreenPass(solverPass)
        addOffscreenPass(advectionPass)


        camera.position.set(0f, 0f, 10f)

        // draw storage texture on a simple quad mesh
        addTextureMesh {
            generate {
                centeredRect {
                    size.set(2f, 2f)
                }
            }
            shader = outputShader
        }

        addNode(obstacleMesh.apply {
            generate {
                rotate(90f.deg, Vec3f.X_AXIS)
                cylinder {
                    radius = 1f / simHeight * 11.5f
                    height = 0f
                    steps = 40
                    topFill = true
                    bottomFill = false
                }
            }
            transform.translate(-7.35f, -0.025f, 0f)
            transform.scale(copyShader.obstacleRadius)
            shader = KslUnlitShader {
                color { constColor(MdColor.GREY) }
            }
        })

        var obstacleAng = 0f

        onUpdate {
            if (clearCount > 0) {
                clearCount--
                copyShader.clearFlag = 1

                if (clearCount == 0) {
                    copyShader.clearFlag = 0
                    solverPass.isEnabled = !isPaused.value
                    advectionPass.isEnabled = !isPaused.value
                }
            }
            if (movingObstacle.value) {
                obstacleAng = (obstacleAng + 0.005f) % (2f * PI_F)
                val obstacleY = simHeight / 2f + sin(obstacleAng) * simHeight / 4f
                copyShader.obstaclePos = Vec2f(64f, obstacleY)
            }
        }

        advectionPass.onAfterDraw += {
            if (isSingleStep.value) {
                solverPass.isEnabled = false
                advectionPass.isEnabled = false
                copyPass.isEnabled = false
            }
        }

        // release storage texture when done
        uStateA.releaseWith(this)
        vStateA.releaseWith(this)
        uStateB.releaseWith(this)
        vStateB.releaseWith(this)
        borderState.releaseWith(this)
    }

    private fun initializeSolverTasks() {
        val randomIndices = IncompressibilitySolverShader.makeRandomAccessIndices(simWidth, simHeight)
        for (i in 0 until 200) {
            val offset = Vec2i(i * 31, i * 19)
            val solver = IncompressibilitySolverShader(uStateA, vStateA, borderState, randomIndices, offset)
            solver.overRelaxation = overRelaxation.value
            solvers += solver
            solverPass.addTask(solver).apply {
                isEnabled = i < numIterations.value
            }
        }
    }

    override fun createMenu(menu: DemoMenu, ctx: KoolContext) = menuSurface {
        var flowSpeed by remember(copyShader.flowSpeed) {
            copyShader.flowSpeed = it
            outputShader.flowSpeed = it
        }
        var obstacleRadius by remember(copyShader.obstacleRadius) {
            copyShader.obstacleRadius = it
        }
        var hue by remember(outputShader.hue) {
            outputShader.hue = it
        }
        var drawMode by remember(outputShader.mode) {
            outputShader.mode = it
        }

        MenuSlider2("Flow Speed:", flowSpeed, 0f, 5f) {
            flowSpeed = it
        }
        MenuSlider2("Obstacle Radius:", obstacleRadius, 4f, 30f) {
            obstacleRadius = it
            (obstacleMesh.transform as TrsTransformF).apply {
                scale.set(it, it, it)
                markDirty()
            }
        }
        MenuSlider2("Over Relaxation:", overRelaxation.use(), 1f, 2f) {
            overRelaxation.set(it)
        }
        MenuRow {
            Text("Draw Style:") { labelStyle() }
            ComboBox {
                modifier
                    .width(Grow.Std)
                    .margin(start = sizes.largeGap)
                    .items(DrawMode.entries)
                    .selectedIndex(drawMode)
                    .onItemSelected { drawMode = it }
            }
        }
        MenuSlider2("Solver Iterations:", numIterations.use().toFloat(), 0f, 200f, { it.toString(0) }) {
            numIterations.set(it.roundToInt())
        }
        MenuSlider2("Hue:", hue, 0f, 1f) {
            hue = it
        }
        LabeledSwitch("Pause Simulation", isPaused) {
            copyPass.isEnabled = !it
            solverPass.isEnabled = !it
            advectionPass.isEnabled = !it
        }
        Button("Reset Simulation") {
            modifier
                .alignX(AlignmentX.Center)
                .width(Grow.Std)
                .margin(horizontal = 16.dp, vertical = 24.dp)
                .onClick {
                    clearCount = 2
                }
        }
    }

    private inner class OutputShader : KslShader("Output shader") {
        var hue by uniform1f("uHue", 0.45f)
        var flowSpeed by uniform1f("flowSpeed", copyShader.flowSpeed)
        var mode by uniform1i("uMode", 1)

        init {
            texture2d("drawState", draw)

            val baseColor = (MdColor.PURPLE toneLin 300).toOklab()
            val gradientColors = (0..18).map { i ->
                val ang = i * 20f
                ang to baseColor.shiftHue(ang).toSrgb()
            }
            val gradient = ColorGradient(gradientColors)
            val gradientTex = GradientTexture(gradient, isClamped = false)
            gradientTex.releaseWith(this@FluidDemo.mainScene)
            texture1d("colorScheme", gradientTex)

            pipelineConfig.isWriteDepth = false
            program.code()
        }

        fun KslProgram.code() {
            val uv = interStageFloat2()
            vertexStage {
                main {
                    uv.input set vertexAttribFloat2(Attribute.TEXTURE_COORDS)
                    outPosition set float4Value(vertexAttribFloat3(Attribute.POSITIONS), 1f.const)
                }
            }
            fragmentStage {
                main {
                    val mode = uniformInt1("uMode")
                    val drawState = float4Var(sampleTexture(texture2d("drawState"), uv.output))

                    val smoke = float1Var(drawState.b)

                    val vel = float2Var(drawState.xy)
                    val speed = length(vel) / uniformFloat1("flowSpeed")
                    val angle = float1Var(atan2(vel.y, vel.x) / (2f * PI_F).const) + uniformFloat1("uHue")
                    val color = float3Var(sampleTexture(texture1d("colorScheme"), angle).rgb * speed)

                    `if`(mode eq 0.const) {
                        colorOutput(color)

                    }.elseIf(mode eq 1.const) {
                        colorOutput(color * smoke)

                    }.`else` {
                        smoke set 1f.const - pow(smoke, 1.25f.const)
                        colorOutput(float3Value(smoke, smoke, smoke))
                    }
                }
            }
        }
    }

    enum class DrawMode(val label: String) {
        Velocity("Velocity"),
        SmokeVelocity("Smoke + Velocity"),
        Smoke("Smoke"),;

        override fun toString(): String {
            return label
        }
    }

    companion object {
        private val texProps = TextureProps(
            addressModeU = AddressMode.CLAMP_TO_EDGE,
            addressModeV = AddressMode.CLAMP_TO_EDGE,
            mipMapping = false,
            maxAnisotropy = 1,
            format = TexFormat.R_I32,
            minFilter = FilterMethod.NEAREST,
            magFilter = FilterMethod.NEAREST
        )
        private val texPropsDraw = TextureProps(
            addressModeU = AddressMode.CLAMP_TO_EDGE,
            addressModeV = AddressMode.CLAMP_TO_EDGE,
            mipMapping = false,
            maxAnisotropy = 1,
            format = TexFormat.R_F32,
            minFilter = FilterMethod.LINEAR,
            magFilter = FilterMethod.LINEAR
        )
    }
}