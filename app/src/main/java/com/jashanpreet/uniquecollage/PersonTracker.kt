package com.jashanpreet.uniquecollage

import kotlin.math.abs

class PersonTracker(
    private val similarityThreshold: Float = 0.65f,
    private val maxTimeGapMs: Long = 2000L
) {

    private val tracks = mutableListOf<PersonTrack>()

    fun add(face: FaceEmbedding) {

        var bestTrack: PersonTrack? = null
        var bestSimilarity = -1f

        for (track in tracks) {

            // A track cannot already contain another detection
            // from the exact same video frame.
            val sameTimestamp = track.faces.any {
                it.timestampMs == face.timestampMs
            }

            if (sameTimestamp) {
                continue
            }

            for (existingFace in track.faces) {

                val timeGap = abs(
                    face.timestampMs - existingFace.timestampMs
                )

                if (timeGap > maxTimeGapMs) {
                    continue
                }

                val similarity =
                    EmbeddingUtils.cosineSimilarity(
                        face.embedding,
                        existingFace.embedding
                    )

                if (similarity > bestSimilarity) {
                    bestSimilarity = similarity
                    bestTrack = track
                }
            }
        }

        if (
            bestTrack != null &&
            bestSimilarity >= similarityThreshold
        ) {
            bestTrack.faces.add(face)

            android.util.Log.d(
                "PersonTracker",
                "Face at ${face.timestampMs}ms -> " +
                        "Person ${bestTrack.id} " +
                        "similarity=$bestSimilarity"
            )

        } else {

            val newTrack = PersonTrack(
                id = tracks.size,
                faces = mutableListOf(face)
            )

            tracks.add(newTrack)

            android.util.Log.d(
                "PersonTracker",
                "Face at ${face.timestampMs}ms -> " +
                        "NEW Person ${newTrack.id}"
            )
        }
    }

    fun getTracks(): List<PersonTrack> {
        return tracks.toList()
    }

    fun clear() {
        tracks.clear()
    }
}