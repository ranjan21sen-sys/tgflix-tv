# TGFlix TV (Fire TV / Android TV, landscape WebView app)

Loads https://tgflix.qzz.io/live-tv fullscreen with remote support.

## Remote
- D-pad: move focus (red outline) | OK/Select: click
- Back: web history back -> exit video fullscreen -> press twice to exit
- Play/Pause, FF, RW keys: control the video (FF/RW = 15s)
- Menu (≡): reload

## Build APK
**Option A – Android Studio:** Open this folder -> Build > Build APK(s).
**Option B – GitHub (no install):** push to a repo -> Actions tab -> "Build APK" -> download artifact `app-debug.apk`.

## Install on Fire TV
1. Fire TV: Settings > My Fire TV > Developer Options > ADB debugging + Apps from Unknown Sources ON
2. Get its IP (Settings > My Fire TV > About > Network)
3. `adb connect <ip>:5555` then `adb install app-debug.apk`
   (or use the Downloader app with a link to the APK)

Change the URL in `MainActivity.kt` (`START_URL`).
