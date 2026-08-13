# Lyrics Overlay

Floating lyrics over other apps (`TYPE_APPLICATION_OVERLAY`), overlay permission, fade/scale/color line animation, foreground service.

- Package: `com.ahad.lyricsoverlay`
- minSdk **24**, compileSdk **34**

## Files

- `app/src/main/java/com/ahad/lyricsoverlay/MainActivity.kt` — permission + start/stop
- `app/src/main/java/com/ahad/lyricsoverlay/LyricsOverlayService.kt` — overlay + animations

## GitHub Actions — possible?

**Yes.** Actions can install JDK + Gradle, run `assembleDebug`, and upload the APK as an artifact.

This session’s GitHub App **cannot push** `.github/workflows/*.yml` (needs `workflows` permission). Add the file yourself:

**`.github/workflows/android.yml`**

```yaml
name: Build Android APK

on:
  push:
    branches: ["**"]
  pull_request:
  workflow_dispatch:

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: "17"
      - uses: gradle/actions/setup-gradle@v3
        with:
          gradle-version: "8.4"
      - run: gradle assembleDebug --no-daemon
      - uses: actions/upload-artifact@v4
        with:
          name: app-debug
          path: app/build/outputs/apk/debug/app-debug.apk
```

Then: **Actions → Build Android APK → download `app-debug`**.

Actions **cannot** run the overlay on a phone by itself. It only **builds** the APK (or you can add an emulator job for unit/UI tests). Overlay permission + WindowManager still need a real device or emulator you control.
