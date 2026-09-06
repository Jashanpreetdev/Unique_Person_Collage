package com.jashanpreet.uniquecollage

import android.util.Log
import kotlin.math.abs
import kotlin.math.hypot

class AppearanceTracker(
    private val similarityThreshold: Float = 0.30f,
    private val maxGapMs: Long = 1500L,
    private val minimumCombinedScore: Float = 0.30f
) {

    private val tracks =
        mutableListOf<AppearanceTrack>()

    fun add(face: FaceEmbedding) {

        var bestTrack: AppearanceTrack? = null
        var bestScore = Float.NEGATIVE_INFINITY

        for (track in tracks) {

            if (track.faces.isEmpty()) {
                continue
            }

            val lastFace =
                track.faces.maxByOrNull {
                    it.timestampMs
                } ?: continue

            // --------------------------------------------------------
            // CRITICAL:
            // Two different faces detected at the same timestamp
            // must NEVER be placed into the same appearance.
            // --------------------------------------------------------

            if (
                lastFace.timestampMs ==
                face.timestampMs
            ) {
                continue
            }

            val timeGap =
                abs(
                    face.timestampMs -
                            lastFace.timestampMs
                )

            if (timeGap > maxGapMs) {
                continue
            }

            // --------------------------------------------------------
            // Compare against ALL historical faces.
            // --------------------------------------------------------

            var bestEmbeddingSimilarity =
                Float.NEGATIVE_INFINITY

            for (historicalFace in track.faces) {

                val similarity =
                    EmbeddingUtils.cosineSimilarity(
                        face.embedding,
                        historicalFace.embedding
                    )

                if (
                    similarity >
                    bestEmbeddingSimilarity
                ) {
                    bestEmbeddingSimilarity =
                        similarity
                }
            }

            // --------------------------------------------------------
            // Face centers
            // --------------------------------------------------------

            val currentCenterX =
                (
                        face.faceBounds.left +
                                face.faceBounds.right
                        ) / 2f

            val currentCenterY =
                (
                        face.faceBounds.top +
                                face.faceBounds.bottom
                        ) / 2f

            val lastCenterX =
                (
                        lastFace.faceBounds.left +
                                lastFace.faceBounds.right
                        ) / 2f

            val lastCenterY =
                (
                        lastFace.faceBounds.top +
                                lastFace.faceBounds.bottom
                        ) / 2f

            val distance =
                hypot(
                    currentCenterX - lastCenterX,
                    currentCenterY - lastCenterY
                )

            val averageFaceSize =
                (
                        face.faceBounds.width() +
                                lastFace.faceBounds.width()
                        ) / 2f

            val normalizedDistance =
                if (
                    averageFaceSize > 0f
                ) {
                    distance /
                            averageFaceSize
                } else {
                    Float.MAX_VALUE
                }

            // --------------------------------------------------------
            // Spatial score
            // --------------------------------------------------------

            val spatialScore =
                (
                        1f -
                                normalizedDistance / 2.5f
                        )
                    .coerceIn(
                        0f,
                        1f
                    )

            // --------------------------------------------------------
            // Combined score
            //
            // Embedding remains the dominant signal.
            // --------------------------------------------------------

            val combinedScore =
                bestEmbeddingSimilarity * 0.60f +
                        spatialScore * 0.40f

            Log.d(
                "AppearanceMatch",
                "Face ${face.timestampMs}ms -> " +
                        "Appearance ${track.id}: " +
                        "embedding=$bestEmbeddingSimilarity " +
                        "distance=$normalizedDistance " +
                        "spatial=$spatialScore " +
                        "combined=$combinedScore"
            )

            if (
                bestEmbeddingSimilarity >=
                similarityThreshold &&
                combinedScore >=
                minimumCombinedScore &&
                combinedScore > bestScore
            ) {

                bestScore =
                    combinedScore

                bestTrack =
                    track
            }
        }

        // ------------------------------------------------------------
        // Existing appearance
        // ------------------------------------------------------------

        if (bestTrack != null) {

            bestTrack.addFace(face)

            Log.d(
                "AppearanceTracker",
                "Face ${face.timestampMs}ms -> " +
                        "Appearance ${bestTrack.id} " +
                        "score=$bestScore"
            )

        } else {

            // --------------------------------------------------------
            // New appearance
            // --------------------------------------------------------

            val newTrack =
                AppearanceTrack(
                    id = nextTrackId()
                )

            newTrack.addFace(face)

            tracks.add(
                newTrack
            )

            Log.d(
                "AppearanceTracker",
                "Face ${face.timestampMs}ms -> " +
                        "NEW Appearance ${newTrack.id}"
            )
        }
    }

    private fun nextTrackId(): Int {

        if (tracks.isEmpty()) {
            return 0
        }

        return tracks.maxOf {
            it.id
        } + 1
    }

    // ================================================================
    // MERGE EMBEDDED SINGLETONS
    // ================================================================

    private fun mergeEmbeddedSingletons() {

        var changed = true

        while (changed) {

            changed = false

            val candidates =
                tracks.filter {
                    it.faces.size == 1
                }

            for (candidate in candidates) {

                val candidateFace =
                    candidate.faces.first()

                val containingTracks =
                    tracks.filter { track ->

                        if (
                            track.id ==
                            candidate.id
                        ) {
                            false
                        } else if (
                            track.faces.size < 2
                        ) {
                            false
                        } else if (
                            track.startTimeMs >=
                            candidate.startTimeMs
                        ) {
                            false
                        } else if (
                            track.endTimeMs <=
                            candidate.endTimeMs
                        ) {
                            false
                        } else {

                            // ------------------------------------------------
                            // CRITICAL FIX:
                            //
                            // If the containing appearance already has a
                            // detection at this exact timestamp, the
                            // singleton is another person visible at the
                            // same time.
                            //
                            // Therefore NEVER merge it.
                            // ------------------------------------------------

                            val sameTimestamp =
                                track.faces.any {
                                    it.timestampMs ==
                                            candidateFace.timestampMs
                                }

                            !sameTimestamp
                        }
                    }

                if (
                    containingTracks.isEmpty()
                ) {
                    continue
                }

                var bestContainingTrack:
                        AppearanceTrack? = null

                var bestSpatialScore =
                    Float.NEGATIVE_INFINITY

                for (
                track in containingTracks
                ) {

                    val before =
                        track.faces
                            .filter {
                                it.timestampMs <
                                        candidateFace.timestampMs
                            }
                            .maxByOrNull {
                                it.timestampMs
                            }

                    val after =
                        track.faces
                            .filter {
                                it.timestampMs >
                                        candidateFace.timestampMs
                            }
                            .minByOrNull {
                                it.timestampMs
                            }

                    if (
                        before == null ||
                        after == null
                    ) {
                        continue
                    }

                    val gapBefore =
                        candidateFace.timestampMs -
                                before.timestampMs

                    val gapAfter =
                        after.timestampMs -
                                candidateFace.timestampMs

                    if (
                        gapBefore > maxGapMs ||
                        gapAfter > maxGapMs
                    ) {
                        continue
                    }

                    // --------------------------------------------------------
                    // Candidate position
                    // --------------------------------------------------------

                    val candidateX =
                        (
                                candidateFace.faceBounds.left +
                                        candidateFace.faceBounds.right
                                ) / 2f

                    val candidateY =
                        (
                                candidateFace.faceBounds.top +
                                        candidateFace.faceBounds.bottom
                                ) / 2f

                    val beforeX =
                        (
                                before.faceBounds.left +
                                        before.faceBounds.right
                                ) / 2f

                    val beforeY =
                        (
                                before.faceBounds.top +
                                        before.faceBounds.bottom
                                ) / 2f

                    val afterX =
                        (
                                after.faceBounds.left +
                                        after.faceBounds.right
                                ) / 2f

                    val afterY =
                        (
                                after.faceBounds.top +
                                        after.faceBounds.bottom
                                ) / 2f

                    val distanceBefore =
                        hypot(
                            candidateX - beforeX,
                            candidateY - beforeY
                        )

                    val distanceAfter =
                        hypot(
                            candidateX - afterX,
                            candidateY - afterY
                        )

                    val averageFaceSize =
                        (
                                candidateFace.faceBounds.width() +
                                        before.faceBounds.width() +
                                        after.faceBounds.width()
                                ) / 3f

                    if (
                        averageFaceSize <= 0f
                    ) {
                        continue
                    }

                    val normalizedBefore =
                        distanceBefore /
                                averageFaceSize

                    val normalizedAfter =
                        distanceAfter /
                                averageFaceSize

                    if (
                        normalizedBefore > 2.5f ||
                        normalizedAfter > 2.5f
                    ) {
                        continue
                    }

                    val spatialScore =
                        (
                                (
                                        1f -
                                                normalizedBefore / 2.5f
                                        ) +
                                        (
                                                1f -
                                                        normalizedAfter / 2.5f
                                                )
                                ) / 2f

                    // --------------------------------------------------------
                    // Also check embedding similarity to surrounding frames.
                    // --------------------------------------------------------

                    val similarityBefore =
                        EmbeddingUtils.cosineSimilarity(
                            candidateFace.embedding,
                            before.embedding
                        )

                    val similarityAfter =
                        EmbeddingUtils.cosineSimilarity(
                            candidateFace.embedding,
                            after.embedding
                        )

                    val bestEmbeddingSimilarity =
                        maxOf(
                            similarityBefore,
                            similarityAfter
                        )

                    // Don't merge an unrelated singleton just because
                    // it happens to be spatially close.
                    if (
                        bestEmbeddingSimilarity < 0.30f
                    ) {
                        continue
                    }

                    if (
                        spatialScore >
                        bestSpatialScore
                    ) {

                        bestSpatialScore =
                            spatialScore

                        bestContainingTrack =
                            track
                    }
                }

                if (
                    bestContainingTrack != null
                ) {

                    bestContainingTrack.addFace(
                        candidateFace
                    )

                    tracks.remove(
                        candidate
                    )

                    Log.d(
                        "AppearanceTracker",
                        "MERGED singleton Appearance " +
                                "${candidate.id} into Appearance " +
                                "${bestContainingTrack.id}"
                    )

                    changed = true

                    break
                }
            }
        }
    }

    // ================================================================
    // GET TRACKS
    // ================================================================

    fun getTracks(): List<AppearanceTrack> {

        mergeEmbeddedSingletons()

        Log.d(
            "AppearanceTracker",
            "===== APPEARANCE TRACKS ====="
        )

        for (
        track in tracks.sortedBy {
            it.startTimeMs
        }
        ) {

            Log.d(
                "AppearanceTracker",
                "Appearance ${track.id}: " +
                        "${track.faces.size} detections, " +
                        "${track.startTimeMs}ms -> " +
                        "${track.endTimeMs}ms"
            )
        }

        return tracks
            .sortedBy {
                it.startTimeMs
            }
            .toList()
    }

    fun clear() {
        tracks.clear()
    }
}