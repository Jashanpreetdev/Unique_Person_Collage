package com.jashanpreet.uniquecollage

class FaceClustering(
    private val similarityThreshold: Float = 0.75f
) {

    fun cluster(
        faces: List<FaceEmbedding>
    ): List<FaceCluster> {

        val clusters = mutableListOf<FaceCluster>()

        for (face in faces) {

            var bestCluster: FaceCluster? = null
            var bestSimilarity = -1f

            for (cluster in clusters) {

                for (existingFace in cluster.embeddings) {

                    val similarity =
                        EmbeddingUtils.cosineSimilarity(
                            face.embedding,
                            existingFace.embedding
                        )

                    if (similarity > bestSimilarity) {
                        bestSimilarity = similarity
                        bestCluster = cluster
                    }
                }
            }

            if (
                bestCluster != null &&
                bestSimilarity >= similarityThreshold
            ) {
                bestCluster.embeddings.add(face)
            } else {
                clusters.add(
                    FaceCluster(
                        id = clusters.size,
                        embeddings = mutableListOf(face)
                    )
                )
            }
        }

        return clusters
    }
}