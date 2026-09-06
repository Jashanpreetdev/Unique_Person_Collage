package com.jashanpreet.uniquecollage

import kotlin.math.sqrt

object EmbeddingUtils {

    fun cosineSimilarity(
        a: FloatArray,
        b: FloatArray
    ): Float {

        require(a.size == b.size) {
            "Embeddings must have the same size"
        }

        var dotProduct = 0f
        var magnitudeA = 0f
        var magnitudeB = 0f

        for (i in a.indices) {
            dotProduct += a[i] * b[i]
            magnitudeA += a[i] * a[i]
            magnitudeB += b[i] * b[i]
        }

        val denominator =
            sqrt(magnitudeA) * sqrt(magnitudeB)

        if (denominator == 0f) {
            return 0f
        }

        return dotProduct / denominator
    }
}