# Build

APK: JDK 17, Android SDK Platform 37.0, and Build Tools 36.0.0. Set `JAVA_HOME` and `ANDROID_HOME` (or `local.properties`), then run `bash gradlew :app:assembleRelease`. The APK contains only the `arm64-v8a` ABI; release builds enable R8 and resource shrinking.

Companion module: Python 3, the same `JAVA_HOME` and `ANDROID_HOME`, and the local debug keystore. Run `python companion/build_combined.py`; this compiles the native resource overlay and creates `build/Coogle-Companion-1.3.4.zip`. Copy the updated ZIP to `app/src/main/assets/coogle-companion.zip` before building the APK.

The APK is signed with the local Android debug keystore. The default debug certificate differs between computers.

Lens native-locator regression: complete a normal build, then on a connected arm64 Android device run `python tests/run_lens_dex_test.py --adb <adb path> --serial <device serial> --verify-regression`. It uses cached dependencies from `JAVA_HOME`, `ANDROID_HOME`, and `GRADLE_USER_HOME` (default `~/.gradle`). It checks old and new event IDs, ambiguous anchors, invalid controller shapes, and missing targets in a temporary device directory without installing an app or changing module settings.

Companion regression: after generating the ZIP, run `python -m unittest discover -s companion -p 'test_*.py'`. The APK's `:app:testHookLifecycle` task covers the 1.3.2 upgrade, payload revision, switch synchronization, and update-selection tests. NoMount mode, driver-not-loaded, marker-failure, and Google SYSTEM-state tests run in the companion suite without Root or changes to a real module.

Camera native eligibility: run `python tests/run_camera_dex_test.py --adb <adb path> --serial <device serial> <camera APK path> [another camera APK path]`. It checks production DexKit discovery, reflection binding, and caching in a temporary device directory without installing an app or changing system settings. Live UI, lock-screen, and post-unlock recovery require separate installation testing.

## GitHub Actions

`.github/workflows/build.yml` builds the companion module, embeds it into the APK assets, and assembles the release APK, so the single artifact installs everything: the app offers to install the embedded companion through KernelSU from its status card. Tag pushes (`v*`) also publish a GitHub Release with the APK and the standalone companion ZIP.

For a stable CI signature that matches local debug builds, set these repository secrets: `COOGLE_KEYSTORE_BASE64` (base64 of the keystore, for example your `~/.android/debug.keystore`), `COOGLE_STORE_PASSWORD`, `COOGLE_KEY_ALIAS`, and `COOGLE_KEY_PASSWORD` (`android`/`androiddebugkey`/`android` for the debug keystore). Generate the value with `base64 -i ~/.android/debug.keystore | pbcopy` (macOS) or `base64 -w0 ~/.android/debug.keystore` (Linux/WSL). Without the secrets, CI signs with a runner-generated debug keystore, which differs between runs.
