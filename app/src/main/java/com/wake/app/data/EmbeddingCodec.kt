package com.wake.app.data

import java.nio.ByteBuffer
import java.nio.ByteOrder

fun FloatArray.toByteArray(): ByteArray {
    val buffer = ByteBuffer.allocate(size * Float.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN)
    forEach(buffer::putFloat)
    return buffer.array()
}

fun ByteArray.toFloatArray(): FloatArray? {
    if (size % Float.SIZE_BYTES != 0) return null
    val buffer = ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN)
    return FloatArray(size / Float.SIZE_BYTES) { buffer.float }
}
