package com.jashanpreet.uniquecollage

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.util.Log
import kotlin.math.roundToInt

object CollageGenerator {

    fun createCollage(
        identities: List<PersonIdentity>
    ): Bitmap {

        val collageWidth = 1080
        val collageHeight = 1920

        val collage =
            Bitmap.createBitmap(
                collageWidth,
                collageHeight,
                Bitmap.Config.ARGB_8888
            )

        val canvas =
            Canvas(collage)

        val imagePaint =
            Paint(
                Paint.ANTI_ALIAS_FLAG or
                        Paint.FILTER_BITMAP_FLAG
            )

        canvas.drawColor(
            android.graphics.Color.WHITE
        )

        // ------------------------------------------------------------
        // ALL DETECTED FACES
        // ------------------------------------------------------------

        val allFaces =
            identities
                .flatMap { identity ->
                    identity.appearances
                        .flatMap { appearance ->
                            appearance.faces
                        }
                }

        // ------------------------------------------------------------
        // ONE REPRESENTATIVE PER PERSON
        // ------------------------------------------------------------

        val representatives =
            identities.mapNotNull { identity ->

                val face =
                    selectBestRepresentative(
                        identity = identity,
                        allFaces = allFaces
                    )

                if (face != null) {
                    Pair(identity, face)
                } else {
                    null
                }
            }

        if (
            representatives.isEmpty()
        ) {
            return collage
        }

        // ------------------------------------------------------------
        // LAYOUT
        // ------------------------------------------------------------

        val count =
            representatives.size

        val columns =
            when {
                count == 1 -> 1
                count == 2 -> 2
                count <= 4 -> 2
                else -> 3
            }

        val rows =
            (
                    count + columns - 1
                    ) / columns

        val headerHeight =
            170f

        val horizontalMargin =
            45f

        val verticalGap =
            28f

        val availableWidth =
            collageWidth -
                    horizontalMargin * 2

        val availableHeight =
            collageHeight -
                    headerHeight -
                    55f

        val cellWidth =
            (
                    availableWidth -
                            verticalGap *
                            (columns - 1)
                    ) / columns

        val cellHeight =
            (
                    availableHeight -
                            verticalGap *
                            (rows - 1)
                    ) / rows

        // ------------------------------------------------------------
        // HEADER
        // ------------------------------------------------------------

        val titlePaint =
            Paint(
                Paint.ANTI_ALIAS_FLAG
            )

        titlePaint.color =
            android.graphics.Color.BLACK

        titlePaint.textSize =
            52f

        titlePaint.typeface =
            Typeface.DEFAULT_BOLD

        titlePaint.textAlign =
            Paint.Align.CENTER

        canvas.drawText(
            "UNIQUE PEOPLE",
            collageWidth / 2f,
            80f,
            titlePaint
        )

        val subtitlePaint =
            Paint(
                Paint.ANTI_ALIAS_FLAG
            )

        subtitlePaint.color =
            android.graphics.Color.DKGRAY

        subtitlePaint.textSize =
            28f

        subtitlePaint.textAlign =
            Paint.Align.CENTER

        canvas.drawText(
            "$count people",
            collageWidth / 2f,
            125f,
            subtitlePaint
        )

        // ------------------------------------------------------------
        // CARDS
        // ------------------------------------------------------------

        for (
        index in representatives.indices
        ) {

            val identity =
                representatives[index].first

            val face =
                representatives[index].second

            val personCrop =
                createPersonCrop(
                    face = face,
                    allFaces = allFaces
                )

            if (
                personCrop == null
            ) {
                continue
            }

            val column =
                index % columns

            val row =
                index / columns

            val left =
                horizontalMargin +
                        column *
                        (
                                cellWidth +
                                        verticalGap
                                )

            val top =
                headerHeight +
                        row *
                        (
                                cellHeight +
                                        verticalGap
                                )

            val cellRect =
                RectF(
                    left,
                    top,
                    left + cellWidth,
                    top + cellHeight
                )

            // --------------------------------------------------------
            // CARD
            // --------------------------------------------------------

            val cardPaint =
                Paint(
                    Paint.ANTI_ALIAS_FLAG
                )

            cardPaint.color =
                android.graphics.Color.WHITE

            canvas.drawRoundRect(
                cellRect,
                24f,
                24f,
                cardPaint
            )

            // --------------------------------------------------------
            // IMAGE
            // --------------------------------------------------------

            val imageRect =
                RectF(
                    cellRect.left + 8f,
                    cellRect.top + 8f,
                    cellRect.right - 8f,
                    cellRect.bottom - 55f
                )

            drawBitmapFit(
                canvas = canvas,
                bitmap = personCrop,
                destination = imageRect,
                paint = imagePaint
            )

            // --------------------------------------------------------
            // LABEL
            // --------------------------------------------------------

            val labelPaint =
                Paint(
                    Paint.ANTI_ALIAS_FLAG
                )

            labelPaint.color =
                android.graphics.Color.WHITE

            labelPaint.textSize =
                26f

            labelPaint.typeface =
                Typeface.DEFAULT_BOLD

            labelPaint.textAlign =
                Paint.Align.CENTER

            val labelX =
                cellRect.centerX()

            val labelY =
                cellRect.bottom - 22f

            val labelBackgroundPaint =
                Paint(
                    Paint.ANTI_ALIAS_FLAG
                )

            labelBackgroundPaint.color =
                android.graphics.Color.BLACK

            val labelRect =
                RectF(
                    labelX - 85f,
                    labelY - 32f,
                    labelX + 85f,
                    labelY + 8f
                )

            canvas.drawRoundRect(
                labelRect,
                18f,
                18f,
                labelBackgroundPaint
            )

            canvas.drawText(
                "Person ${identity.id + 1}",
                labelX,
                labelY,
                labelPaint
            )

            if (
                !personCrop.isRecycled
            ) {
                personCrop.recycle()
            }
        }

        return collage
    }

    // ================================================================
    // REPRESENTATIVE SELECTION
    // ================================================================

    private fun selectBestRepresentative(
        identity: PersonIdentity,
        allFaces: List<FaceEmbedding>
    ): FaceEmbedding? {

        val candidates =
            identity.appearances
                .flatMap { appearance ->
                    appearance.faces.map { face ->
                        Pair(
                            appearance,
                            face
                        )
                    }
                }

        if (
            candidates.isEmpty()
        ) {
            return null
        }

        // ------------------------------------------------------------
        // Prefer frames where only one face is detected.
        //
        // This is the safest representative whenever such a frame
        // exists for the person.
        // ------------------------------------------------------------

        val singlePersonCandidates =
            candidates.filter { (_, face) ->

                val facesAtSameTimestamp =
                    allFaces.count {
                        it.timestampMs ==
                                face.timestampMs
                    }

                facesAtSameTimestamp == 1
            }

        val usableCandidates =
            if (
                singlePersonCandidates.isNotEmpty()
            ) {
                singlePersonCandidates
            } else {
                candidates
            }

        return usableCandidates.maxByOrNull {
                (appearance, face) ->

            val quality =
                FaceQualityScorer.score(
                    face
                )

            val detectionCount =
                appearance.faces.size

            val stabilityBonus =
                when {
                    detectionCount >= 5 ->
                        0.12f

                    detectionCount >= 3 ->
                        0.08f

                    detectionCount == 2 ->
                        0.03f

                    else ->
                        -0.08f
                }

            val faceArea =
                face.faceBounds.width().toFloat() *
                        face.faceBounds.height().toFloat()

            val sizeBonus =
                (
                        faceArea / 10000f
                        )
                    .coerceIn(
                        0f,
                        0.10f
                    )

            quality +
                    stabilityBonus +
                    sizeBonus

        }?.second
    }

    // ================================================================
    // CREATE PERSON CROP
    // ================================================================

    private fun createPersonCrop(
        face: FaceEmbedding,
        allFaces: List<FaceEmbedding>
    ): Bitmap? {

        val frame =
            face.bitmap

        if (
            frame.isRecycled ||
            frame.width <= 0 ||
            frame.height <= 0
        ) {
            return null
        }

        val bounds =
            face.faceBounds

        if (
            bounds.width() <= 0 ||
            bounds.height() <= 0
        ) {
            return null
        }

        val facesAtSameTimestamp =
            allFaces.filter {
                it.timestampMs ==
                        face.timestampMs
            }

        // ============================================================
        // SPECIAL CASE:
        // MULTIPLE PEOPLE IN THE SAME FRAME
        // ============================================================

        if (
            facesAtSameTimestamp.size >= 2
        ) {

            val faceCenterX =
                (
                        bounds.left +
                                bounds.right
                        ) / 2

            // Find the horizontally nearest other face.
            val otherFace =
                facesAtSameTimestamp
                    .filter {
                        it !== face
                    }
                    .minByOrNull {

                        val otherCenterX =
                            (
                                    it.faceBounds.left +
                                            it.faceBounds.right
                                    ) / 2

                        kotlin.math.abs(
                            otherCenterX -
                                    faceCenterX
                        )
                    }

            if (
                otherFace != null
            ) {

                val otherCenterX =
                    (
                            otherFace.faceBounds.left +
                                    otherFace.faceBounds.right
                            ) / 2

                val isLeftPerson =
                    faceCenterX <
                            otherCenterX

                // ----------------------------------------------------
                // Boundary between the two people.
                // ----------------------------------------------------

                val midpoint =
                    (
                            faceCenterX +
                                    otherCenterX
                            ) / 2

                var left: Int
                var right: Int

                if (
                    isLeftPerson
                ) {

                    left = 0
                    right = midpoint

                } else {

                    left = midpoint
                    right = frame.width
                }

                // ----------------------------------------------------
                // Safety margin from the neighboring person.
                // ----------------------------------------------------

                val safetyMargin =
                    (
                            bounds.width() *
                                    0.08f
                            )
                        .roundToInt()

                if (
                    isLeftPerson
                ) {

                    right =
                        (
                                right -
                                        safetyMargin
                                )
                            .coerceAtLeast(
                                bounds.right
                            )

                } else {

                    left =
                        (
                                left +
                                        safetyMargin
                                )
                            .coerceAtMost(
                                bounds.left
                            )
                }

                // ----------------------------------------------------
                // Region belonging to the target person.
                // ----------------------------------------------------

                val availableWidth =
                    right - left

                if (
                    availableWidth >=
                    bounds.width()
                ) {

                    val targetAspect =
                        9f / 16f

                    var cropWidth =
                        availableWidth

                    var cropHeight =
                        (
                                cropWidth /
                                        targetAspect
                                )
                            .roundToInt()

                    // If the portrait crop is taller than the
                    // original frame, reduce it.
                    if (
                        cropHeight >
                        frame.height
                    ) {

                        cropHeight =
                            frame.height

                        cropWidth =
                            (
                                    cropHeight *
                                            targetAspect
                                    )
                                .roundToInt()
                                .coerceAtMost(
                                    availableWidth
                                )
                    }

                    if (
                        cropWidth > 0 &&
                        cropHeight > 0
                    ) {

                        // Center inside the target person's region.
                        val regionCenterX =
                            (
                                    left +
                                            right
                                    ) / 2

                        var cropLeft =
                            regionCenterX -
                                    cropWidth / 2

                        var cropTop =
                            (
                                    (
                                            bounds.top +
                                                    bounds.bottom
                                            ) / 2
                                    ) -
                                    (
                                            cropHeight *
                                                    0.38f
                                            )
                                        .roundToInt()

                        cropLeft =
                            cropLeft.coerceIn(
                                left,
                                right - cropWidth
                            )

                        cropTop =
                            cropTop.coerceIn(
                                0,
                                frame.height -
                                        cropHeight
                            )

                        return try {

                            // IMPORTANT:
                            // Use cropLeft/cropTop here.
                            // These are the coordinates calculated
                            // specifically for the target person's
                            // side of the split frame.

                            Bitmap.createBitmap(
                                frame,
                                cropLeft,
                                cropTop,
                                cropWidth,
                                cropHeight
                            )

                        } catch (
                            e: Exception
                        ) {

                            Log.e(
                                "CollageGenerator",
                                "Failed split-screen crop",
                                e
                            )

                            null
                        }
                    }
                }
            }
        }

        // ============================================================
        // NORMAL SINGLE-PERSON FRAME
        // ============================================================

        val targetAspect =
            9f / 16f

        var cropWidth =
            (
                    bounds.width() *
                            2.0f
                    )
                .roundToInt()
                .coerceAtMost(
                    frame.width
                )

        var cropHeight =
            (
                    cropWidth /
                            targetAspect
                    )
                .roundToInt()

        if (
            cropHeight >
            frame.height
        ) {

            cropHeight =
                frame.height

            cropWidth =
                (
                        cropHeight *
                                targetAspect
                        )
                    .roundToInt()
                    .coerceAtMost(
                        frame.width
                    )
        }

        if (
            cropWidth <= 0 ||
            cropHeight <= 0
        ) {
            return null
        }

        val faceCenterX =
            (
                    bounds.left +
                            bounds.right
                    ) / 2

        val faceCenterY =
            (
                    bounds.top +
                            bounds.bottom
                    ) / 2

        var left =
            faceCenterX -
                    cropWidth / 2

        var top =
            faceCenterY -
                    (
                            cropHeight *
                                    0.38f
                            )
                        .roundToInt()

        left =
            left.coerceIn(
                0,
                frame.width -
                        cropWidth
            )

        top =
            top.coerceIn(
                0,
                frame.height -
                        cropHeight
            )

        return try {

            Bitmap.createBitmap(
                frame,
                left,
                top,
                cropWidth,
                cropHeight
            )

        } catch (
            e: Exception
        ) {

            Log.e(
                "CollageGenerator",
                "Failed normal crop",
                e
            )

            null
        }
    }

    // ================================================================
    // DRAW WITHOUT DISTORTION
    // ================================================================

    private fun drawBitmapFit(
        canvas: Canvas,
        bitmap: Bitmap,
        destination: RectF,
        paint: Paint
    ) {

        val sourceWidth =
            bitmap.width.toFloat()

        val sourceHeight =
            bitmap.height.toFloat()

        if (
            sourceWidth <= 0f ||
            sourceHeight <= 0f
        ) {
            return
        }

        val sourceAspect =
            sourceWidth /
                    sourceHeight

        val destinationAspect =
            destination.width() /
                    destination.height()

        val drawRect: RectF

        if (
            sourceAspect >
            destinationAspect
        ) {

            val height =
                destination.width() /
                        sourceAspect

            val top =
                destination.centerY() -
                        height / 2f

            drawRect =
                RectF(
                    destination.left,
                    top,
                    destination.right,
                    top + height
                )

        } else {

            val width =
                destination.height() *
                        sourceAspect

            val left =
                destination.centerX() -
                        width / 2f

            drawRect =
                RectF(
                    left,
                    destination.top,
                    left + width,
                    destination.bottom
                )
        }

        canvas.drawBitmap(
            bitmap,
            null,
            drawRect,
            paint
        )
    }
}