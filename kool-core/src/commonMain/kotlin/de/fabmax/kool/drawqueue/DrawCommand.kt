package de.fabmax.kool.drawqueue

import de.fabmax.kool.KoolContext
import de.fabmax.kool.math.Mat4f
import de.fabmax.kool.pipeline.PipelineConfig

abstract class DrawCommand {

    val modelMat = Mat4f()
    val viewMat = Mat4f()
    val projMat = Mat4f()
    val mvpMat = Mat4f()

    var pipelineConfig: PipelineConfig? = null

    open fun captureMvp(ctx: KoolContext) {
        modelMat.set(ctx.mvpState.modelMatrix)
        viewMat.set(ctx.mvpState.viewMatrix)
        projMat.set(ctx.mvpState.projMatrix)
        mvpMat.set(ctx.mvpState.mvpMatrix)
    }

    fun applyMvp(ctx: KoolContext) {
        ctx.mvpState.apply {
            modelMatrix.set(modelMat)
            viewMatrix.set(viewMat)
            projMatrix.set(projMat)
            mvpMatrix.set(mvpMat)
        }
    }
}