package com.jashanpreet.uniquecollage

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs

object FaceQualityScorer {

    fun score(
        face: FaceEmbedding
    ): Float {

        // ============================================================
        // 1. FRONTALITY
        // ============================================================

        val yawScore =
            (
                    1f -
                            abs(face.headEulerAngleY) / 45f
                    )
                .coerceIn(0f, 1f)

        val rollScore =
            (
                    1f -
                            abs(face.headEulerAngleZ) / 30f
                    )
                .coerceIn(0f, 1f)

        val frontalityScore =
            (
                    yawScore +
                            rollScore
                    ) / 2f

        // ============================================================
        // 2. EYES OPEN
        // ============================================================

        val eyeScore =
            if (
                face.leftEyeOpenProbability != null &&
                face.rightEyeOpenProbability != null
            ) {

                (
                        face.leftEyeOpenProbability +
                                face.rightEyeOpenProbability
                        ) / 2f

            } else {

                // ML Kit may not always provide probabilities.
                0.5f
            }

        // ============================================================
        // 3. SMILE / PLEASANT EXPRESSION
        // ============================================================

        val smileScore =
            face.smilingProbability
                ?: 0.5f

        // ============================================================
        // 4. FACE SIZE
        //
        // Prefer reasonably large faces, but don't require
        // extremely large crops.
        // ============================================================

        val faceArea =
            face.faceBounds.width() *
                    face.faceBounds.height()

        val sizeScore =
            (
                    faceArea / 2500f
                    )
                .coerceIn(0f, 1f)

        // ============================================================
        // 5. FACE COMPLETENESS
        //
        // Penalize faces that are very close to the frame edge.
        // ============================================================

        val frame =
            face.bitmap

        val bounds =
            face.faceBounds

        val edgeMargin =
            minOf(
                bounds.left,
                bounds.top,
                frame.width - bounds.right,
                frame.height - bounds.bottom
            )

        val faceDimension =
            minOf(
                bounds.width(),
                bounds.height()
            )
                .coerceAtLeast(1)

        val normalizedEdgeMargin =
            edgeMargin.toFloat() /
                    faceDimension.toFloat()

        val completenessScore =
            (
                    normalizedEdgeMargin / 0.5f
                    )
                .coerceIn(0f, 1f)

        // ============================================================
        // 6. SHARPNESS
        //
        // Estimate local image detail using luminance differences.
        //
        // A blurry face generally has lower high-frequency detail.
        // ============================================================

        val sharpnessScore =
            calculateSharpnessScore(
                frame,
                bounds
            )

        // ============================================================
        // FINAL QUALITY SCORE
        // ============================================================

        val finalScore =
            frontalityScore * 0.25f +
                    eyeScore * 0.20f +
                    smileScore * 0.10f +
                    sizeScore * 0.20f +
                    completenessScore * 0.10f +
                    sharpnessScore * 0.15f

        return finalScore
            .coerceIn(0f, 1f)
    }

    // ============================================================
    // SHARPNESS ESTIMATION
    // ============================================================

    private fun calculateSharpnessScore(
        bitmap: Bitmap,
        bounds: android.graphics.Rect
    ): Float {

        try {

            // ------------------------------------------------
            // Keep the sampling region safely inside bitmap.
            // ------------------------------------------------

            val left =
                bounds.left
                    .coerceIn(
                        0,
                        bitmap.width - 1
                    )

            val top =
                bounds.top
                    .coerceIn(
                        0,
                        bitmap.height - 1
                    )

            val right =
                bounds.right
                    .coerceIn(
                        left + 1,
                        bitmap.width
                    )

            val bottom =
                bounds.bottom
                    .coerceIn(
                        top + 1,
                        bitmap.height
                    )

            val width =
                right - left

            val height =
                bottom - top

            if (
                width < 8 ||
                height < 8
            ) {
                return 0.5f
            }

            // ------------------------------------------------
            // Sample every few pixels.
            // ------------------------------------------------

            val step =
                4

            var variation =
                0.0

            var count =
                0

            var y =
                top

            while (
                y < bottom - step
            ) {

                var x =
                    left

                while (
                    x < right - step
                ) {

                    val pixel1 =
                        bitmap.getPixel(
                            x,
                            y
                        )

                    val pixel2 =
                        bitmap.getPixel(
                            x + step,
                            y
                        )

                    val pixel3 =
                        bitmap.getPixel(
                            x,
                            y + step
                        )

                    val luminance1 =
                        luminance(
                            pixel1
                        )

                    val luminance2 =
                        luminance(
                            pixel2
                        )

                    val luminance3 =
                        luminance(
                            pixel3
                        )

                    variation +=
                        abs(
                            luminance1 -
                                    luminance2
                        )

                    variation +=
                        abs(
                            luminance1 -
                                    luminance3
                        )

                    count += 2

                    x += step
                }

                y += step
            }

            if (
                count == 0
            ) {
                return 0.5f
            }

            val averageVariation =
                variation /
                        count

            /*
             * Typical values are image-dependent.
             * We normalize into a practical 0..1 range.
             *
             * Higher local luminance variation
             * generally means more visible detail.
             */

            return (
                    averageVariation / 35.0
                    )
                .toFloat()
                .coerceIn(
                    0f,
                    1f
                )

        } catch (e: Exception) {

            return 0.5f
        }
    }

    // ============================================================
    // LUMINANCE
    // ============================================================

    private fun luminance(
        pixel: Int
    ): Double {

        val red =
            Color.red(pixel)

        val green =
            Color.green(pixel)

        val blue =
            Color.blue(pixel)

        return (
                0.299 * red +
                        0.587 * green +
                        0.114 * blue
                )
    }
}