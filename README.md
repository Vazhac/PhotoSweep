# PhotoSweep

PhotoSweep is an Android photo-library cleanup app built with Kotlin and Jetpack Compose. It helps users review their gallery, protect keepers, queue unwanted items, and confirm deletion through Android's system flow.

## What it does

- Requests the appropriate image-library permission for the device's Android version.
- Loads and sorts images from `MediaStore` by capture date.
- Lets users review a gallery one image at a time, with swipe gestures or Keep and Mark delete actions.
- Supports Undo during a swipe session and allows individual photos to be protected from cleanup.
- Provides filters for all photos, screenshots, large files, likely duplicates, and old photos.
- Groups likely duplicate files using normalized names, then shows the newest item first.
- Offers an Auto Clean flow for screenshots, large files, duplicates, similar shots, possible low-quality photos, selfies, documents, and text-heavy images.
- Runs its smart scan on-device with ML Kit face detection and text recognition, plus basic image-sharpness and metadata checks.
- Caches smart-scan results while the library and protected-photo list remain unchanged.
- Shows a review grid before deletion, with options to restore individual items or clear the whole queue.
- Uses Android's deletion confirmation request on Android 11 and later; older supported versions delete through `ContentResolver`.
- Persists session progress, selected deletion items, protected items, scan results, and the deletion count in local app preferences.

## Feature availability

The app includes a simple in-memory premium flag for prototyping. In the current implementation, premium-gated features are duplicate cleanup, similar shots, possible low-quality photos, selfies, documents, text-heavy images, and video access. It is not connected to billing or durable account entitlement yet.

## Requirements

- Android Studio with a Java 17 JDK configured.
- Android SDK 35 for compiling the project.
- An Android 10 (API 29) or newer device or emulator. A real device with photos is recommended for meaningful gallery, thumbnail, ML Kit, and deletion-flow testing.

## Run the app

1. Open the `PhotoSweep` folder in Android Studio.
2. Allow Gradle to sync and install any required Android SDK components.
3. Choose a device or emulator, then run the `app` configuration.
4. Grant photo-library access when prompted.

## Test

Run the local unit tests from Android Studio, or from a terminal with a configured Java 17 JDK:

```bash
./gradlew testDebugUnitTest
```

The current tests cover smart-scan signature stability, duplicate grouping, Auto Clean selection and premium locks, session normalization, and cleanup of missing scan IDs.

## Project structure

- `app/src/main/java/com/photosweep/app/MainActivity.kt` contains the activity, Compose UI, view model, repository, gallery access, cleanup logic, and smart-scan helpers.
- `app/src/test/java/com/photosweep/app/MainActivityLogicTest.kt` contains local logic tests.
- `app/src/main/AndroidManifest.xml` declares gallery permissions and the launcher activity.

## Current limitations

- The app currently reads images only; videos are represented by a premium check but are not loaded from `MediaStore` in the present repository implementation.
- Smart cleanup uses heuristics and should be treated as a suggestion, not a guarantee. Users should review queued photos before confirming deletion.
- The premium state is not persistent and has no billing integration.
- The app is implemented primarily in one Kotlin source file, which is practical for an MVP but should be split into data, domain, UI, and feature-specific files as it grows.

## Tech stack

- Kotlin, Jetpack Compose, Material 3, and AndroidX lifecycle components
- Coil for image loading
- Google ML Kit Face Detection and Text Recognition
- Robolectric and JUnit for local unit tests
