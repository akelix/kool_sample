package de.fabmax.kool.pipeline.backend.webgpu

import de.fabmax.kool.pipeline.backend.GpuGeometry
import de.fabmax.kool.scene.Mesh
import de.fabmax.kool.util.BaseReleasable
import de.fabmax.kool.util.checkIsNotReleased

class WgpuGeometry(val mesh: Mesh, val backend: RenderBackendWebGpu) : BaseReleasable(), GpuGeometry {
    private val device: GPUDevice get() = backend.device

    private val createdIndexBuffer: WgpuVertexBuffer
    private val createdFloatBuffer: WgpuVertexBuffer
    private val createdIntBuffer: WgpuVertexBuffer?

    val indexBuffer: GPUBuffer get() = createdIndexBuffer.buffer.buffer
    val floatBuffer: GPUBuffer get() = createdFloatBuffer.buffer.buffer
    val intBuffer: GPUBuffer? get() = createdIntBuffer?.buffer?.buffer

    private var isNewlyCreated = true

    init {
        val geom = mesh.geometry
        createdIndexBuffer = WgpuVertexBuffer(backend, "${mesh.name} index data", 4 * geom.numIndices, GPUBufferUsage.INDEX or GPUBufferUsage.COPY_DST)
        createdFloatBuffer = WgpuVertexBuffer(backend, "${mesh.name} vertex float data", geom.byteStrideF * geom.numVertices)
        createdIntBuffer = if (geom.byteStrideI == 0) null else {
            WgpuVertexBuffer(backend, "${mesh.name} vertex int data", geom.byteStrideI * geom.numVertices)
        }
    }

    fun checkBuffers() {
        checkIsNotReleased()

        val geometry = mesh.geometry
        if (!geometry.isBatchUpdate && (geometry.hasChanged || isNewlyCreated)) {
            createdIndexBuffer.writeData(geometry.indices)
            createdFloatBuffer.writeData(geometry.dataF)
            createdIntBuffer?.writeData(geometry.dataI)
            geometry.hasChanged = false
        }
        isNewlyCreated = false
    }

    override fun release() {
        super.release()
        createdIndexBuffer.buffer.release()
        createdFloatBuffer.buffer.release()
        createdIntBuffer?.buffer?.release()
    }
}