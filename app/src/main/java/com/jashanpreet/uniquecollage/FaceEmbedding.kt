package com.jashanpreet.uniquecollage

import android.graphics.Bitmap
import android.graphics.Rect

data class FaceEmbedding(
    val timestampMs: Long,
    val embedding: FloatArray,
    val bitmap: Bitmap,

    // Information from ML Kit
    val faceBounds: Rect,
    val headEulerAngleY: Float,
    val headEulerAngleZ: Float,
    val leftEyeOpenProbability: Float?,
    val rightEyeOpenProbability: Float?,
    val smilingProbability: Float?
)