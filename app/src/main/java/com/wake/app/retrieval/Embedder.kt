package com.wake.app.retrieval

import android.content.Context
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.text.textembedder.TextEmbedder
import kotlin.math.sqrt

class Embedder(context: Context) {
    private val context = context.applicationContext
    private var textEmbedder: TextEmbedder? = null
    private var unavailable = false

    @Synchronized
    fun ensure(): Boolean {
        if (unavailable) return false
        if (textEmbedder != null) return true
        return runCatching {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath("universal_sentence_encoder.tflite")
                .build()
            val options = TextEmbedder.TextEmbedderOptions.builder()
                .setBaseOptions(baseOptions)
                .build()
            textEmbedder = TextEmbedder.createFromOptions(context, options)
            true
        }.getOrElse {
            unavailable = true
            false
        }
    }

    fun embed(text: String): FloatArray? {
        if (!ensure()) return null
        return runCatching {
            textEmbedder!!.embed(text.take(1000))
                .embeddingResult()
                .embeddings()[0]
                .floatEmbedding()
        }.getOrNull()
    }

    companion object {
        fun cosine(a: FloatArray, b: FloatArray): Float {
            if (a.size != b.size || a.isEmpty()) return 0f
            var dot = 0f
            var aMagnitude = 0f
            var bMagnitude = 0f
            a.indices.forEach { index ->
                dot += a[index] * b[index]
                aMagnitude += a[index] * a[index]
                bMagnitude += b[index] * b[index]
            }
            val denominator = sqrt(aMagnitude) * sqrt(bMagnitude)
            return if (denominator == 0f) 0f else dot / denominator
        }
    }
}
