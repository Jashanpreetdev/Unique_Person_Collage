package com.jashanpreet.uniquecollage

import android.content.ContentValues
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var collageImageView: ImageView

    private var currentCollage: Bitmap? = null

    // ------------------------------------------------------------
    // VIDEO PICKER
    // ------------------------------------------------------------

    private val videoPicker = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->

        if (uri != null) {
            processVideo(uri)
        }
    }

    // ------------------------------------------------------------
    // ON CREATE
    // ------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(
            findViewById(R.id.main)
        ) { view, insets ->

            val systemBars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars()
            )

            view.setPadding(
                systemBars.left, systemBars.top, systemBars.right, systemBars.bottom
            )

            insets
        }

        statusText = findViewById(R.id.statusText)

        collageImageView = findViewById(R.id.collageImageView)

        val selectVideoButton = findViewById<Button>(
            R.id.selectVideoButton
        )

        val saveCollageButton = findViewById<Button>(
            R.id.saveCollageButton
        )

        // Select video
        selectVideoButton.setOnClickListener {
            videoPicker.launch("video/*")
        }

        // Save collage
        saveCollageButton.setOnClickListener {

            val collage = currentCollage

            if (collage == null) {

                Toast.makeText(
                    this, "Create a collage first", Toast.LENGTH_SHORT
                ).show()

            } else {

                saveCollageToGallery(
                    collage
                )
            }
        }
    }

    // ------------------------------------------------------------
    // PROCESS VIDEO
    // ------------------------------------------------------------

    private fun processVideo(
        videoUri: Uri
    ) {

        statusText.text = "Processing video..."

        collageImageView.setImageDrawable(null)

        currentCollage = null

        lifecycleScope.launch {

            try {

                // ====================================================
                // 1. EXTRACT FRAMES
                // ====================================================

                statusText.text = "Extracting frames..."

                val extractor = VideoFrameExtractor(
                    this@MainActivity
                )

                val frames = extractor.extractFrames(
                    videoUri
                )

                Log.d(
                    "MainActivity", "Frames extracted: ${frames.size}"
                )

                statusText.text = "Frames: ${frames.size}\n" + "Detecting faces..."

                // ====================================================
                // 2. FACE DETECTION
                // ====================================================

                val faceDetector = FaceDetector()

                try {

                    val detectedFaces = faceDetector.detectFaces(
                        frames
                    )

                    Log.d(
                        "MainActivity", "Faces detected: ${detectedFaces.size}"
                    )

                    statusText.text =
                        "Frames: ${frames.size}\n" + "Faces: ${detectedFaces.size}\n" + "Generating embeddings..."

                    // ====================================================
                    // 3. FACE EMBEDDINGS
                    // ====================================================

                    val embedder = FaceEmbedder(
                        this@MainActivity
                    )

                    try {

                        val embeddings = mutableListOf<FaceEmbedding>()

                        for (detectedFace in detectedFaces) {

                            val face = detectedFace.face

                            val frame = detectedFace.frame

                            val bounds = face.boundingBox

                            // Ignore very small faces
                            if (bounds.width() < 25 || bounds.height() < 25) {
                                continue
                            }

                            // ------------------------------------------------
// Square, generously padded crop for FaceNet
// ------------------------------------------------

                            val faceBitmap = createFaceEmbeddingCrop(
                                frame.bitmap, bounds
                            )

                            if (faceBitmap == null) {
                                continue
                            }

                            try {

                                val embedding = embedder.getEmbedding(
                                    faceBitmap
                                )

                                embeddings.add(
                                    FaceEmbedding(
                                        timestampMs = frame.timestampMs,

                                        embedding = embedding,

                                        bitmap = frame.bitmap,

                                        faceBounds = bounds,

                                        headEulerAngleY = face.headEulerAngleY,

                                        headEulerAngleZ = face.headEulerAngleZ,

                                        leftEyeOpenProbability = face.leftEyeOpenProbability,

                                        rightEyeOpenProbability = face.rightEyeOpenProbability,

                                        smilingProbability = face.smilingProbability
                                    )
                                )

                            } finally {

                                faceBitmap.recycle()
                            }
                        }

                        // ====================================================
                        // 4. SORT EMBEDDINGS
                        // ====================================================

                        val sortedEmbeddings = embeddings.sortedBy {
                            it.timestampMs
                        }

                        Log.d(
                            "MainActivity", "Embeddings: ${sortedEmbeddings.size}"
                        )

                        statusText.text =
                            "Frames: ${frames.size}\n" + "Faces: ${detectedFaces.size}\n" + "Embeddings: ${sortedEmbeddings.size}\n" + "Finding appearances..."

                        // ====================================================
                        // 5. APPEARANCE TRACKING
                        // ====================================================

                        val appearanceTracker = AppearanceTracker()

                        for (embedding in sortedEmbeddings) {

                            appearanceTracker.add(
                                embedding
                            )
                        }

                        val appearanceTracks = appearanceTracker.getTracks()

                        Log.d(
                            "AppearanceTracker", "===== APPEARANCE TRACKS ====="
                        )

                        for (track in appearanceTracks) {

                            Log.d(
                                "AppearanceTracker",
                                "Appearance ${track.id}: " + "${track.faces.size} detections, " + "${track.startTimeMs}ms -> " + "${track.endTimeMs}ms"
                            )
                        }

                        // ====================================================
                        // 6. IDENTITY CLUSTERING
                        // ====================================================

                        statusText.text =
                            "Frames: ${frames.size}\n" + "Faces: ${detectedFaces.size}\n" + "Appearances: ${appearanceTracks.size}\n" + "Identifying people..."

                        val identityClusterer = IdentityClusterer(
                            similarityThreshold = 0.60f
                        )

                        for (appearance in appearanceTracks) {

                            identityClusterer.addAppearance(
                                appearance
                            )
                        }

                        val identities = identityClusterer.getIdentities()

                        Log.d(
                            "IdentityClusterer", "===== FINAL PEOPLE ====="
                        )

                        for (identity in identities) {

                            Log.d(
                                "IdentityClusterer",
                                "Person ${identity.id + 1}: " + "${identity.appearanceCount} appearances, " + "${identity.detectionCount} detections"
                            )

                            for (appearance in identity.appearances) {

                                Log.d(
                                    "IdentityClusterer",
                                    "Appearance ${appearance.id}: " + "${appearance.startTimeMs}ms -> " + "${appearance.endTimeMs}ms"
                                )
                            }
                        }

                        // ====================================================
                        // 7. BEST REPRESENTATIVE FRAMES
                        // ====================================================

                        Log.d(
                            "FaceQuality", "===== BEST REPRESENTATIVE FRAMES ====="
                        )

                        for (identity in identities) {

                            for (appearance in identity.appearances) {

                                val bestFace = appearance.getBestFace()

                                if (bestFace != null) {

                                    Log.d(
                                        "FaceQuality",
                                        "Person ${identity.id + 1}, " + "Appearance ${appearance.id} -> " + "best frame=${bestFace.timestampMs}ms " + "score=${
                                            FaceQualityScorer.score(bestFace)
                                        }"
                                    )
                                }
                            }
                        }

                        // ====================================================
                        // 8. CREATE COLLAGE
                        // ====================================================

                        statusText.text =
                            "Frames: ${frames.size}\n" + "Faces: ${detectedFaces.size}\n" + "Appearances: ${appearanceTracks.size}\n" + "Unique people: ${identities.size}\n\n" + "Creating collage..."

                        val collage = CollageGenerator.createCollage(
                            identities
                        )

                        currentCollage = collage

                        collageImageView.setImageBitmap(
                            collage
                        )

                        Log.d(
                            "CollageGenerator",
                            "Collage created: " + "${collage.width}x${collage.height}"
                        )

                        // ====================================================
                        // 9. FINAL RESULT
                        // ====================================================

                        val result = StringBuilder()

                        result.append(
                            "Frames: ${frames.size}\n"
                        )

                        result.append(
                            "Faces: ${detectedFaces.size}\n"
                        )

                        result.append(
                            "Embeddings: ${sortedEmbeddings.size}\n"
                        )

                        result.append(
                            "Appearances: ${appearanceTracks.size}\n"
                        )

                        result.append(
                            "Unique people: ${identities.size}\n\n"
                        )

                        for (identity in identities) {

                            result.append(
                                "Person ${identity.id + 1}: " + "${identity.appearanceCount} appearances, " + "${identity.detectionCount} detections\n"
                            )
                        }

                        statusText.text = result.toString()

                    } finally {

                        embedder.close()
                    }

                } finally {

                    faceDetector.close()
                }

                Toast.makeText(
                    this@MainActivity, "Processing complete", Toast.LENGTH_SHORT
                ).show()

            } catch (e: Exception) {

                Log.e(
                    "MainActivity", "Error processing video", e
                )

                statusText.text = "Error processing video"

                Toast.makeText(
                    this@MainActivity, e.message ?: "Unknown error", Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // ------------------------------------------------------------
// CREATE SQUARE FACE CROP FOR FACENET
// ------------------------------------------------------------

    private fun createFaceEmbeddingCrop(
        frame: Bitmap,
        bounds: android.graphics.Rect
    ): Bitmap? {

        if (
            frame.isRecycled ||
            frame.width <= 0 ||
            frame.height <= 0
        ) {
            return null
        }

        if (
            bounds.width() <= 0 ||
            bounds.height() <= 0
        ) {
            return null
        }

        // Use the larger face dimension so that
        // the crop is always square.
        val faceSize =
            maxOf(
                bounds.width(),
                bounds.height()
            )

        // Generous context around the face.
        // The face occupies roughly half of the crop.
        val cropSize =
            (faceSize * 2.0f)
                .toInt()
                .coerceAtLeast(80)
                .coerceAtMost(
                    minOf(
                        frame.width,
                        frame.height
                    )
                )

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

        // Move the crop slightly upward because
        // the useful facial region is above the
        // exact center of a person's head/shoulders.
        var left =
            faceCenterX -
                    cropSize / 2

        var top =
            faceCenterY -
                    (cropSize * 0.55f).toInt()

        left =
            left.coerceIn(
                0,
                frame.width - cropSize
            )

        top =
            top.coerceIn(
                0,
                frame.height - cropSize
            )

        return try {

            Bitmap.createBitmap(
                frame,
                left,
                top,
                cropSize,
                cropSize
            )

        } catch (e: Exception) {

            Log.e(
                "FaceEmbedding",
                "Could not create face crop",
                e
            )

            null
        }
    }

    // ------------------------------------------------------------
    // SAVE COLLAGE TO GALLERY
    // ------------------------------------------------------------

    private fun saveCollageToGallery(
        bitmap: Bitmap
    ) {

        try {

            val fileName = "UniquePersonCollage_${System.currentTimeMillis()}.jpg"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {

                // Android 10+
                val values = ContentValues().apply {

                    put(
                        MediaStore.Images.Media.DISPLAY_NAME, fileName
                    )

                    put(
                        MediaStore.Images.Media.MIME_TYPE, "image/jpeg"
                    )

                    put(
                        MediaStore.Images.Media.RELATIVE_PATH,
                        Environment.DIRECTORY_PICTURES + "/UniquePersonCollage"
                    )

                    put(
                        MediaStore.Images.Media.IS_PENDING, 1
                    )
                }

                val uri = contentResolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
                )

                if (uri == null) {

                    Toast.makeText(
                        this, "Could not save collage", Toast.LENGTH_LONG
                    ).show()

                    return
                }

                try {

                    contentResolver.openOutputStream(uri).use { output ->

                            if (output == null) {
                                throw Exception(
                                    "Could not open output stream"
                                )
                            }

                            val success = bitmap.compress(
                                Bitmap.CompressFormat.JPEG, 95, output
                            )

                            if (!success) {
                                throw Exception(
                                    "Could not compress collage"
                                )
                            }
                        }

                    val completedValues = ContentValues().apply {

                        put(
                            MediaStore.Images.Media.IS_PENDING, 0
                        )
                    }

                    contentResolver.update(
                        uri, completedValues, null, null
                    )

                    Toast.makeText(
                        this, "Collage saved to Gallery", Toast.LENGTH_LONG
                    ).show()

                    Log.d(
                        "CollageSave", "Saved: $uri"
                    )

                } catch (e: Exception) {

                    contentResolver.delete(
                        uri, null, null
                    )

                    throw e
                }

            } else {

                // Android 9 and below
                val picturesDirectory = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_PICTURES
                )

                val directory = File(
                    picturesDirectory, "UniquePersonCollage"
                )

                if (!directory.exists()) {
                    directory.mkdirs()
                }

                val file = File(
                    directory, fileName
                )

                FileOutputStream(file).use { output ->

                    val success = bitmap.compress(
                        Bitmap.CompressFormat.JPEG, 95, output
                    )

                    if (!success) {
                        throw Exception(
                            "Could not compress collage"
                        )
                    }
                }

                Toast.makeText(
                    this, "Collage saved to Gallery", Toast.LENGTH_LONG
                ).show()

                Log.d(
                    "CollageSave", "Saved: ${file.absolutePath}"
                )
            }

        } catch (e: Exception) {

            Log.e(
                "CollageSave", "Failed to save collage", e
            )

            Toast.makeText(
                this, "Failed to save collage: ${e.message}", Toast.LENGTH_LONG
            ).show()
        }
    }

    // ------------------------------------------------------------
    // CLEANUP
    // ------------------------------------------------------------

    override fun onDestroy() {

        currentCollage = null

        super.onDestroy()
    }
}