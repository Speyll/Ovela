# Ovela for Android TV / Google TV

Native Kotlin/Compose application using AndroidX Media3 ExoPlayer.

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Signed release builds are documented in
[`docs/releasing.md`](../../docs/releasing.md#android-signing).

See [`docs/development/android-tv.md`](../../docs/development/android-tv.md) for
TCL, Sony, emulator, ADB, and wireless-debugging instructions.
