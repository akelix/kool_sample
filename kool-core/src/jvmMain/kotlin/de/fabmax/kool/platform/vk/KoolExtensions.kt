package de.fabmax.kool.platform.vk

import de.fabmax.kool.math.Mat4f
import de.fabmax.kool.shading.AttributeType
import de.fabmax.kool.util.IndexedVertexList
import org.lwjgl.vulkan.VK10.*
import org.lwjgl.vulkan.VkVertexInputAttributeDescription
import org.lwjgl.vulkan.VkVertexInputBindingDescription
import kotlin.math.PI
import kotlin.math.tan

fun Mat4f.setPerspective2(fovy: Float, aspect: Float, near: Float, far: Float): Mat4f {
    val s = 1f / tan(fovy * (PI / 360.0)).toFloat()

    matrix[offset + 0] = s / aspect
    matrix[offset + 1] = 0.0f
    matrix[offset + 2] = 0.0f
    matrix[offset + 3] = 0.0f

    matrix[offset + 4] = 0.0f
    matrix[offset + 5] = s
    matrix[offset + 6] = 0.0f
    matrix[offset + 7] = 0.0f

    matrix[offset + 8] = 0.0f
    matrix[offset + 9] = 0.0f
    matrix[offset + 10] = -far / (far - near)
    matrix[offset + 11] = -1.0f

    matrix[offset + 12] = 0.0f
    matrix[offset + 13] = 0.0f
    matrix[offset + 14] = -(far * near) / (far - near)
    matrix[offset + 15] = 0.0f

    return this
}

fun VkVertexInputBindingDescription.Buffer.setBindingDescription(vertices: IndexedVertexList, binding: Int = 0) {
    binding(binding)
    stride(vertices.strideBytesF)
    inputRate(VK_VERTEX_INPUT_RATE_VERTEX)
}

fun VkVertexInputAttributeDescription.Buffer.setAttributeDescription(vertices: IndexedVertexList, binding: Int = 0) {
    var iAttrib = 0
    vertices.attributeOffsets.forEach { (attrib, off) ->
        this[iAttrib].apply {
            binding(binding)
            location(iAttrib)
            offset(off * 4)
            when (attrib.type) {
                AttributeType.FLOAT -> format(VK_FORMAT_R32_SFLOAT)
                AttributeType.VEC_2F -> format(VK_FORMAT_R32G32_SFLOAT)
                AttributeType.VEC_3F -> format(VK_FORMAT_R32G32B32_SFLOAT)
                AttributeType.VEC_4F -> format(VK_FORMAT_R32G32B32A32_SFLOAT)
                AttributeType.COLOR_4F -> format(VK_FORMAT_R32G32B32A32_SFLOAT)
                else -> throw IllegalStateException("Attribute is not a float type")
            }
        }
        iAttrib++
    }
}
