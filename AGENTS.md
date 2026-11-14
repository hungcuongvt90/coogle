# Coogle fork instructions

## Testing

- Add or update tests for every code change, even when not explicitly requested.
- Run the checks for the changed area and report what actually passed. Do not describe focused checks as a passing full suite.
- Preserve unrelated working-tree changes when syncing upstream or fixing an issue.

## Checking ColorOS logic

- This fork supports ColorOS 16 and 17. Compare upstream changes against the known working COS16 support commit `5588334`, especially `SystemCompatibility`, both `CoogleModule` entry points, and `NavigationHooks`. Check behavior, rather than assuming an upstream update preserves the COS16 fallback.
- Verify that COS16 properties such as `V16.1.0` pass eligibility and dispatch into both package hooks and system-server hooks. Keep the named COS16 navigation path alongside the newer renamed/DexKit path.
- Inspect raw DEX names and actual reflection signatures on the target firmware. JADX's Kotlin aliases can differ from compiled names. On the tested COS16 Launcher, `OplusHideNavHandleController$INSTANCE.isSupportCircleToSearchHandle()` is an instance method; `NavigationSettingsValueProxy` uses a `$Companion` holder. Its settings holder has no `INSTANCE` field. The navigation observer and handle controller still use their own `INSTANCE` holders.
- Match fixtures to those real signatures. Accept the supported boolean gate's static or instance dispatch as appropriate; do not accidentally require a static method on the COS16 path.
- Preflight the navigation path before installing hooks. On failure, roll back only the affected hooks and preserve unrelated hooks. Test failure and rollback as well as success.
- Preserve native navigation preferences and feature-off behavior. Do not force the Launcher's global EXP flag or overwrite secure settings to mask a binding failure. Check caller deoptimization when a native gate is inlined.

Run the focused navigation/platform regressions after changes in these areas:

```sh
bash gradlew :app:testNavigationColorOs16 :app:testRenamedNavigation :app:testSystemCompatibilityTest :app:testSystemCompatibilityDispatchTest
```

## Checking companion and NoMount logic

- Respect the user's selected mount backend. Do not switch metamodules to work around an unverified hook problem.
- Check mount preparation separately from runtime hooks. For an active metamodule, keep Coogle's static product overlay parked; retain the capability XML only after the existing idmap preflight passes. Configure the provider through the runtime fabricated overlay after boot.
- Keep Binder/package-manager calls out of pre-mount preparation. Preserve Google app systemization, user component choices, and Play Store registration when changing translation preparation or startup order.
- Report pending activation, incompatible overlays, component failures, and successful configuration accurately. A true capability check or green status does not prove the gesture or translated output works.
- Keep the builder's payload revision and `CompanionInstaller.REQUIRED_PAYLOAD_REVISION` consistent when the required payload changes. Verify upgrade and staged-module behavior for such changes.

With the Android/JDK environment from `BUILDING.md` configured, use:

```sh
python3 companion/build_combined.py
python3 companion/test_combined.py
python3 companion/test_native_translation.py
python3 companion/test_startup.py
```

Other companion tests cover mount backends and Google registration. Some older fixtures hard-code Windows Bash paths; report host limitations rather than claiming those tests passed. The older combined hook fixture has also hit a JDK 21.0.2 compiler error; use the focused tasks above to check the changed behavior and report that limitation if it recurs.

## Device verification

- Do not initiate a normal reboot or KernelSU soft reboot unless the user explicitly requests that action. The user normally performs required KSU soft reboots. Do not clear app data or replace root/mount configuration without a request.
- Reload only the affected app process for Launcher-only changes. APK updates do not replace hooks already loaded into `system_server`; changes there, or companion mount/capability changes, need user-performed activation before verification.
- Record the relevant process IDs and kernel boot ID so activation and process reloads are distinguishable.
- Use relevant log tags and narrow queries; broad vendor logcat output can contain credentials.
- Test the bottom navigation hold on an unlocked phone. Confirm CTS actually opens; then check Translate and translated content before claiming end-to-end translation success. Check wallpaper/color and the Play Store launcher registration when investigating mount regressions.
- Remove temporary diagnostic modules and their hooks/files before final verification. Do not rely on a probe to make production behavior work.
- Investigation details and previous evidence: `docs/live-translation-nomount-2026-11-14.md`.

## Release naming and installation

- Use `python3 package_release.py` after assembling the APK. Default outputs are `build/releases/<version>/COS16-Coogle-<version>.apk`, `COS16-Coogle-Companion-<version>.zip`, and `SHA256SUMS`.
- Read the release version from the built APK's metadata. The standalone companion must match the bytes embedded in that APK, and its version must match the APK version.
- The `COS16-` prefix applies to downloadable files. Keep `assets/coogle-companion.zip`, the installer's `coogle-companion.zip` cache filename, the `coogle_companion` module ID, and the existing installation paths unchanged. Do not change the application ID or signing identity for a filename change.
- CI and local packaging must use the same script. CI passes `--output-dir dist`; `v*` tag publishing must use `gh release create --prerelease` and upload both prefixed files with their checksums.
- Test release publishing with a fake `gh` command when checking arguments locally; do not create a remote release just to test the workflow.

```sh
python3 -m unittest discover -s tests -p test_package_release.py
```
