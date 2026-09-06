# Unique Person Collage

An Android application that processes portrait videos on-device, detects faces, groups appearances belonging to the same person, selects a representative frame, and generates a shareable collage.

## Features

- Select a portrait video from the device
- On-device face detection using ML Kit
- Face embeddings using a FaceNet 512-dimensional model
- Groups multiple appearances of the same person
- Displays the appearance count for each person
- Selects a representative high-quality frame
- Generates an Instagram Story-style collage
- Save the generated collage
- Share the collage using the Android share sheet
- No backend/server processing

## Tech Stack

- Kotlin
- Android SDK
- XML layouts
- Google ML Kit Face Detection
- LiteRT / TensorFlow Lite
- Kotlin Coroutines
- Minimum SDK: 26

## Face Embedding Model

The application uses the following on-device FaceNet model:

`facenet_512_int_quantized.tflite`

Model characteristics:

- Input: `160 × 160 × 3`
- Output: `512-dimensional embedding`
- Runs completely on-device
- Model file is included in:

`app/src/main/assets/`

The input image is normalized to the `[-1, 1]` range before inference.

## Similarity Thresholds

The identity clustering similarity threshold is:

`0.68`

An appearance-tracking embedding threshold of:

`0.30`

is also used during temporal appearance tracking.

These thresholds were selected empirically during testing to balance identity grouping and separation of different people.

## Processing Pipeline

1. User selects a portrait video.
2. Video frames are sampled for processing.
3. ML Kit detects faces in the frames.
4. Face regions are extracted.
5. FaceNet generates a 512-dimensional embedding for each detected face.
6. Temporally close detections are grouped into appearance tracks.
7. Appearance embeddings are compared to group appearances.
8. Appearances are clustered into unique person identities.
9. A representative frame is selected using face-quality factors such as:
   - Face frontality
   - Eye openness
   - Smile probability
   - Face size
   - Face completeness
   - Sharpness
10. A final collage is generated for all identified people.
11. The collage can be saved or shared.

## Build and Setup

### Requirements

- Android Studio
- Android SDK
- JDK supported by the Android Studio project
- Android device or emulator

### Steps

1. Clone this repository.
2. Open the project in Android Studio.
3. Allow Gradle synchronization to complete.
4. Make sure the `facenet_512_int_quantized.tflite` model is present in:

   `app/src/main/assets/`

5. Select the `debug` build variant.
6. Build and run the application.
7. Select a portrait video and start processing.

## APK

A working debug APK is provided separately as:

`app-debug.apk`

## Architecture

The application separates the major processing responsibilities into components for:

- Face detection
- Face embedding generation
- Appearance tracking
- Identity clustering
- Face quality scoring
- Collage generation

All face analysis and embedding generation are performed on-device.

## Output

The generated collage contains each detected unique person once, together with their appearance count.

The collage is designed in a portrait/Instagram Story-style format and can be saved to the device gallery or shared using Android's standard sharing mechanism.

