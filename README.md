# PhotoSweep

PhotoSweep is a basic Android gallery cleaning app.

## What this version does

It loads images from the device gallery, shows one at a time, lets the user swipe or tap Keep and Delete, then shows a review grid before actual deletion.

## Current MVP features

1. Photo permission request
2. Gallery scan through MediaStore
3. Swipe right to keep
4. Swipe left to mark for deletion
5. Keep, Delete, and Undo buttons
6. Review screen for marked items
7. Android delete confirmation flow

## Open in Android Studio

1. Open the `PhotoSweep` folder in Android Studio
2. Let Gradle sync
3. Run the app on a device or emulator
4. Build APK from Android Studio using Build > Build APK(s)

## Notes

This project targets modern Android storage rules and uses MediaStore for gallery access.

The main source file is:

`app/src/main/java/com/photosweep/app/MainActivity.kt`
