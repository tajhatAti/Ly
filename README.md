# Lyrics Overlay (Android)

Floating lyrics overlay like Musixmatch — `WindowManager` + `TYPE_APPLICATION_OVERLAY`, `SYSTEM_ALERT_WINDOW`, fade/scale/color animation, foreground service.

- Package: `com.ahad.lyricsoverlay`
- minSdk 24 / compileSdk 34

## GitHub Actions

Yes — this repo builds the APK on every push via [`.github/workflows/android.yml`](.github/workflows/android.yml).

1. Push this branch
2. Open **Actions** → **Build Android APK**
3. Download the `app-debug` artifact

## Local

Open in Android Studio (Giraffe+) or:

```bash
gradle assembleDebug
```

Grant **Display over other apps**, then tap **Start floating lyrics**.
