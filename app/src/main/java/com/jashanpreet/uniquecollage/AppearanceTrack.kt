package com.jashanpreet.uniquecollage

import kotlin.math.sqrt

data class AppearanceTrack(
    val id: Int,
    val faces: MutableList<FaceEmbedding> = mutableListOf()
) {

    fun addFace(face: FaceEmbedding) {
        faces.add(face)
    }

    val startTimeMs: Long
        get() = faces.minOf { it.timestampMs }

    val endTimeMs: Long
        get() = faces.maxOf { it.timestampMs }

    val durationMs: Long
        get() = endTimeMs - startTimeMs

    fun getBestFace(): FaceEmbedding? {
        val bestFace =
            faces.maxByOrNull { face ->
                FaceQualityScorer.score(face)
            }

        if (bestFace != null) {
            android.util.Log.d(
                "FaceQuality",
                "Appearance $id -> best frame=${bestFace.timestampMs}ms " +
                        "score=${FaceQualityScorer.score(bestFace)}"
            )
        }

        return bestFace
    }

    /*
     * Create a representative embedding using the best-quality
     * embeddings from this appearance.
     *
     * Instead of relying on only one frame, we use up to 3
     * high-quality frames and average their embeddings.
     */
    fun getRepresentativeEmbedding(): FloatArray? {

        if (faces.isEmpty()) {
            return null
        }

        val selectedFaces =
            faces
                .sortedByDescending {
                    FaceQualityScorer.score(it)
                }
                .take(3)

        val embeddingSize =
            selectedFaces.first().embedding.size

        val average =
            FloatArray(embeddingSize)

        for (face in selectedFaces) {

            for (i in average.indices) {
                average[i] += face.embedding[i]
            }
        }

        for (i in average.indices) {
            average[i] /= selectedFaces.size
        }

        return normalize(average)
    }

    /*
     * L2-normalize an embedding.
     */
    private fun normalize(
        embedding: FloatArray
    ): FloatArray {

        var magnitude = 0.0

        for (value in embedding) {
            magnitude += value * value
        }

        val length =
            sqrt(magnitude)

        if (length == 0.0) {
            return embedding.copyOf()
        }

        return FloatArray(
            embedding.size
        ) { index ->
            (
                    embedding[index] / length
                    ).toFloat()
        }
    }
}