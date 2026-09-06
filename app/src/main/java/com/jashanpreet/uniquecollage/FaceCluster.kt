package com.jashanpreet.uniquecollage

data class FaceCluster(
    val id: Int,
    val embeddings: MutableList<FaceEmbedding>
)