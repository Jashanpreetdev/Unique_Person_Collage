package com.jashanpreet.uniquecollage

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.Interpreter
import kotlin.math.sqrt

class FaceEmbedder(context: Context) {

    private val interpreter: Interpreter

    init {
        val model = context.assets.openFd("facenet_512_int_quantized.tflite")

        val inputStream = java.io.FileInputStream(model.fileDescriptor)

        val modelBuffer = inputStream.channel.map(
            java.nio.channels.FileChannel.MapMode.READ_ONLY,
            model.startOffset,
            model.declaredLength
        )

        interpreter = Interpreter(modelBuffer)

        val inputTensor = interpreter.getInputTensor(0)
        val outputTensor = interpreter.getOutputTensor(0)

        Log.d(
            "FaceEmbedder",
            "INPUT shape=${inputTensor.shape().contentToString()} " +
                    "type=${inputTensor.dataType()}"
        )

        Log.d(
            "FaceEmbedder",
            "OUTPUT shape=${outputTensor.shape().contentToString()} " +
                    "type=${outputTensor.dataType()}"
        )
    }

    fun getEmbedding(bitmap: Bitmap): FloatArray {

        // FaceNet expects 160 x 160 RGB image
        val resizedBitmap = Bitmap.createScaledBitmap(
            bitmap,
            160,
            160,
            true
        )

        // Input tensor: [1][160][160][3]
        val input = Array(1) {
            Array(160) {
                Array(160) {
                    FloatArray(3)
                }
            }
        }

        for (y in 0 until 160) {
            for (x in 0 until 160) {

                val pixel = resizedBitmap.getPixel(x, y)

                val r = (pixel shr 16 and 0xFF)
                val g = (pixel shr 8 and 0xFF)
                val b = (pixel and 0xFF)

                // Normalize RGB values to [-1, 1]
                input[0][y][x][0] = (r - 127.5f) / 127.5f
                input[0][y][x][1] = (g - 127.5f) / 127.5f
                input[0][y][x][2] = (b - 127.5f) / 127.5f
            }
        }

        // Output tensor: [1][512]
        val output = Array(1) {
            FloatArray(512)
        }

        interpreter.run(input, output)

        // L2 normalize embedding
        var magnitude = 0f

        for (value in output[0]) {
            magnitude += value * value
        }

        magnitude = sqrt(magnitude)

        val embedding = FloatArray(512)

        for (i in 0 until 512) {
            embedding[i] = output[0][i] / magnitude
        }

        return embedding
    }

    fun close() {
        interpreter.close()
    }
}