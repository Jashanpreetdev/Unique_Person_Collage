package com.jashanpreet.uniquecollage

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.tasks.await

data class DetectedFace(
    val face: Face,
    val frame: VideoFrame
)

class FaceDetector {

    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setClassificationMode(
                FaceDetectorOptions.CLASSIFICATION_MODE_ALL
            )
            .setMinFaceSize(0.1f)
            .build()
    )

    suspend fun detectFaces(
        frames: List<VideoFrame>
    ): List<DetectedFace> {

        val detectedFaces = mutableListOf<DetectedFace>()

        for (frame in frames) {

            val image = InputImage.fromBitmap(
                frame.bitmap,
                0
            )

            val faces = detector.process(image).await()

            for (face in faces) {
                detectedFaces.add(
                    DetectedFace(
                        face = face,
                        frame = frame
                    )
                )
            }
        }

        return detectedFaces
    }

    fun close() {
        detector.close()
    }
}