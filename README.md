# PhotoSweep

PhotoSweep is an Android photo-library cleanup app built with Kotlin and Jetpack Compose. It helps users review their gallery, protect keepers, queue unwanted items, and confirm deletion through Android's system flow.

## What it does

- Requests the appropriate image-library permission for the device's Android version.
- Loads and sorts images from `MediaStore` by capture date.
- Lets users review a gallery one image at a time with swipe gestures: left marks a photo for deletion and right keeps it.
- Supports Undo during a swipe session and allows individual photos to be protected from cleanup.
- Provides filters for all photos, screenshots, large files, likely duplicates, and old photos.
- Groups likely duplicate files using normalized names, then shows the newest item first.
- Offers a Cleanup Suggestions flow for screenshots, large files, duplicates, similar shots, possible low-quality photos, selfies, documents, and text-heavy images. It never deletes automatically; selected suggestions enter the normal review flow.
- Runs its smart scan on-device with ML Kit face detection and text recognition, plus basic image-sharpness and metadata checks.
- Caches smart-scan results while the library and protected-photo list remain unchanged.
- Shows a review grid before deletion, with options to restore individual items or clear the whole queue.
- Uses Android's deletion confirmation request on Android 11 and later; older supported versions delete through `ContentResolver`.
- Persists session progress, selected deletion items, protected items, scan results, and the deletion count in local app preferences.

## Free and premium feature availability

PhotoSweep currently uses a simple in-memory premium flag for prototyping. There is no billing or durable entitlement yet.

- **Free:** unlimited photo review, keep, delete, undo, protect, and basic filters for all photos, screenshots, large files, and old photos. Cleanup Suggestions is fully available for screenshots and large files.
- **Premium prototype:** advanced Cleanup Suggestions for duplicates, similar shots, possible low-quality photos, selfies, documents, and text-heavy images. Full video support is planned but is not loaded yet.
- Locked Cleanup Suggestions remain visible with real counts and a small preview. They can be unlocked only by the temporary in-app prototype switch.

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

- `app/src/main/java/com/photosweep/app/MainActivity.kt` contains only Android startup, permission handling, and the deletion-result launcher.
- `app/src/main/java/com/photosweep/app/data/` contains shared models, premium state, gallery access, and persisted session storage.
- `app/src/main/java/com/photosweep/app/domain/` contains filters, cleanup categories, duplicate and similar-shot grouping, and cleanup-selection rules.
- `app/src/main/java/com/photosweep/app/analysis/` contains image sharpness and face-layout helpers used by the on-device smart scan.
- `app/src/main/java/com/photosweep/app/viewmodel/` contains `PhotoSweepViewModel`, which coordinates app state and user actions.
- `app/src/main/java/com/photosweep/app/ui/` contains the Compose theme, screens, and reusable UI components.
- `app/src/test/java/com/photosweep/app/MainActivityLogicTest.kt` contains local logic tests.
- `app/src/main/AndroidManifest.xml` declares gallery permissions and the launcher activity.

## Current limitations

- The app currently reads images only; videos are represented by a premium check but are not loaded from `MediaStore` in the present repository implementation.
- Smart cleanup uses heuristics and should be treated as a suggestion, not a guarantee. Users should review queued photos before confirming deletion.
- The premium state is not persistent and has no billing integration.
- The premium state is a temporary local prototype; Google Play Billing and persistent entitlement restoration are still required before release.

## Tech stack

- Kotlin, Jetpack Compose, Material 3, and AndroidX lifecycle components
- Coil for image loading
- Google ML Kit Face Detection and Text Recognition
- Robolectric and JUnit for local unit tests
