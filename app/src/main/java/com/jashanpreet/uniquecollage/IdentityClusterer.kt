package com.jashanpreet.uniquecollage

import android.util.Log

class IdentityClusterer(
    private val similarityThreshold: Float = 0.68f,
    private val minimumStrongMatches: Int = 1
) {

    private val identities =
        mutableListOf<PersonIdentity>()

    // ================================================================
    // TEMPORAL OVERLAP
    // ================================================================

    private fun appearancesOverlap(
        first: AppearanceTrack,
        second: AppearanceTrack
    ): Boolean {

        return first.startTimeMs <= second.endTimeMs &&
                second.startTimeMs <= first.endTimeMs
    }

    // ================================================================
    // REPRESENTATIVE EMBEDDING
    // ================================================================

    private fun getAppearanceEmbedding(
        appearance: AppearanceTrack
    ): FloatArray? {

        return appearance.getRepresentativeEmbedding()
    }

    // ================================================================
    // NORMALIZE
    // ================================================================

    private fun normalize(
        values: FloatArray
    ): FloatArray {

        var magnitude = 0.0

        for (value in values) {
            magnitude += value * value
        }

        val length =
            kotlin.math.sqrt(magnitude)

        if (length == 0.0) {
            return values.copyOf()
        }

        return FloatArray(
            values.size
        ) { index ->

            (
                    values[index] /
                            length
                    ).toFloat()
        }
    }

    // ================================================================
    // CREATE IDENTITY CENTROID
    // ================================================================

    private fun getIdentityCentroid(
        identity: PersonIdentity
    ): FloatArray? {

        val embeddings =
            identity.appearances.mapNotNull {
                getAppearanceEmbedding(it)
            }

        if (embeddings.isEmpty()) {
            return null
        }

        val size =
            embeddings.first().size

        val average =
            FloatArray(size)

        for (embedding in embeddings) {

            for (i in 0 until size) {

                average[i] +=
                    embedding[i]
            }
        }

        for (i in 0 until size) {

            average[i] /=
                embeddings.size
                    .toFloat()
        }

        return normalize(
            average
        )
    }

    // ================================================================
    // APPEARANCE VS IDENTITY
    // ================================================================

    private fun similarityToIdentity(
        appearance: AppearanceTrack,
        identity: PersonIdentity
    ): Float {

        val appearanceEmbedding =
            getAppearanceEmbedding(
                appearance
            ) ?: return -1f

        val centroid =
            getIdentityCentroid(
                identity
            ) ?: return -1f

        return EmbeddingUtils.cosineSimilarity(
            appearanceEmbedding,
            centroid
        )
    }

    // ================================================================
    // STRONGEST INDIVIDUAL APPEARANCE MATCH
    // ================================================================

    private fun strongestAppearanceMatch(
        appearance: AppearanceTrack,
        identity: PersonIdentity
    ): Float {

        val appearanceEmbedding =
            getAppearanceEmbedding(
                appearance
            ) ?: return -1f

        var best =
            Float.NEGATIVE_INFINITY

        for (
        existingAppearance
        in identity.appearances
        ) {

            if (
                appearancesOverlap(
                    appearance,
                    existingAppearance
                )
            ) {
                continue
            }

            val existingEmbedding =
                getAppearanceEmbedding(
                    existingAppearance
                ) ?: continue

            val similarity =
                EmbeddingUtils.cosineSimilarity(
                    appearanceEmbedding,
                    existingEmbedding
                )

            if (
                similarity > best
            ) {
                best =
                    similarity
            }
        }

        return best
    }

    // ================================================================
    // ADD APPEARANCE
    // ================================================================

    fun addAppearance(
        appearance: AppearanceTrack
    ) {

        var bestIdentity:
                PersonIdentity? = null

        var bestScore =
            Float.NEGATIVE_INFINITY

        var bestCentroidSimilarity =
            Float.NEGATIVE_INFINITY

        var bestStrongMatch =
            Float.NEGATIVE_INFINITY

        for (
        identity
        in identities
        ) {

            // --------------------------------------------------------
            // Check temporal overlap first.
            // --------------------------------------------------------

            val overlaps =
                identity.appearances.any {
                        existingAppearance ->

                    appearancesOverlap(
                        appearance,
                        existingAppearance
                    )
                }

            if (overlaps) {

                Log.d(
                    "IdentitySimilarity",
                    "Appearance ${appearance.id} " +
                            "SKIPPED identity ${identity.id} " +
                            "- temporal overlap"
                )

                continue
            }

            // --------------------------------------------------------
            // Centroid similarity
            // --------------------------------------------------------

            val centroidSimilarity =
                similarityToIdentity(
                    appearance,
                    identity
                )

            // --------------------------------------------------------
            // Strongest previous appearance.
            // Used only as supporting evidence.
            // --------------------------------------------------------

            val strongMatch =
                strongestAppearanceMatch(
                    appearance,
                    identity
                )

            Log.d(
                "IdentitySimilarity",
                "Appearance ${appearance.id} -> " +
                        "Person ${identity.id}: " +
                        "centroid=$centroidSimilarity " +
                        "strongest=$strongMatch"
            )

            // --------------------------------------------------------
            // We primarily trust the centroid.
            //
            // A single unusually similar frame should not dominate
            // the identity decision.
            // --------------------------------------------------------

            val score =
                centroidSimilarity * 0.75f +
                        strongMatch.coerceAtLeast(0f) *
                        0.25f

            // --------------------------------------------------------
            // Require a reasonable centroid similarity.
            // --------------------------------------------------------

            if (
                centroidSimilarity >=
                similarityThreshold &&
                score > bestScore
            ) {

                bestScore =
                    score

                bestIdentity =
                    identity

                bestCentroidSimilarity =
                    centroidSimilarity

                bestStrongMatch =
                    strongMatch
            }
        }

        // ============================================================
        // ASSIGN TO EXISTING IDENTITY
        // ============================================================

        if (
            bestIdentity != null &&
            bestCentroidSimilarity >=
            similarityThreshold
        ) {

            bestIdentity.addAppearance(
                appearance
            )

            Log.d(
                "IdentityClusterer",
                "Appearance ${appearance.id} -> " +
                        "Person ${bestIdentity.id} " +
                        "centroid=$bestCentroidSimilarity " +
                        "strongest=$bestStrongMatch " +
                        "score=$bestScore"
            )

        } else {

            // ========================================================
            // CREATE NEW PERSON
            // ========================================================

            val newIdentity =
                PersonIdentity(
                    id = identities.size
                )

            newIdentity.addAppearance(
                appearance
            )

            identities.add(
                newIdentity
            )

            Log.d(
                "IdentityClusterer",
                "Appearance ${appearance.id} -> " +
                        "NEW Person ${newIdentity.id}"
            )
        }
    }

    // ================================================================
    // GET IDENTITIES
    // ================================================================

    fun getIdentities(): List<PersonIdentity> {

        return identities
            .sortedBy {
                it.appearances.minOfOrNull {
                        appearance ->
                    appearance.startTimeMs
                } ?: Long.MAX_VALUE
            }
            .toList()
    }

    fun clear() {
        identities.clear()
    }
}