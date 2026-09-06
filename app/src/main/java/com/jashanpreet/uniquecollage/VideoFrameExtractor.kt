package com.jashanpreet.uniquecollage

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class VideoFrame(
    val bitmap: Bitmap,
    val timestampMs: Long
)

class VideoFrameExtractor(
    private val context: Context
) {

    suspend fun extractFrames(
        videoUri: Uri,
        intervalMs: Long = 500L
    ): List<VideoFrame> = withContext(Dispatchers.IO) {

        val retriever = MediaMetadataRetriever()
        val frames = mutableListOf<VideoFrame>()

        try {
            retriever.setDataSource(context, videoUri)

            val durationMs = retriever
                .extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_DURATION
                )
                ?.toLongOrNull()
                ?: 0L

            var timestampMs = 0L

            while (timestampMs < durationMs) {

                val bitmap = retriever.getScaledFrameAtTime(
                    timestampMs * 1000,
                    MediaMetadataRetriever.OPTION_CLOSEST,
                    640,
                    360
                )

                if (bitmap != null) {

                    Log.d(
                        "FrameInfo",
                        "timestamp=${timestampMs}ms " +
                                "size=${bitmap.width}x${bitmap.height}"
                    )

                    frames.add(
                        VideoFrame(
                            bitmap = bitmap,
                            timestampMs = timestampMs
                        )
                    )
                }

                timestampMs += intervalMs
            }

        } finally {
            retriever.release()
        }

        frames
    }
}