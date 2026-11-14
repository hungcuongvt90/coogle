# NoMount live translation investigation

Device: PKJ110, firmware PKJ110_16.0.9.401(CN01), Android 16, NoMount 2.3.0 (720), Google app 17.74.18.ve.arm64. Installed Coogle companion: 1.3.9, payload revision 6.

The companion's metamodule guard parked both the static provider overlay and `coogle-live-translate.xml`. Device checks confirmed `overlay-unavailable`, an empty `config_defaultContextualSearchPackageName`, and `pm has-feature com.google.android.feature.CONTEXTUAL_SEARCH_LIVE_TRANSLATE 0` returning `false`. Google systemization and software hotword detection were active; the user Play Store APK remained registered.

Android accepted a fabricated overlay containing only `android:string/config_defaultContextualSearchPackageName = com.google.android.googlequicksearchbox`. The enabled overlay and idmap reside in `/data/resource-cache`; lookup returned the expected provider with NoMount still active. The `system_server` PID remained 9204 across the operation. No reboot was performed.

The revised preflight was also tested in an isolated directory on the phone: the static APK stayed parked, the capability XML was restored in the fixture, and mode became `runtime`. The runtime helper verified the provider and reported `capability-pending`, because the running SystemConfig had not loaded the previously parked declaration. This is partial device verification, not confirmation that live translation works end to end. The capability scan and user-visible translation still need verification after the user activates the updated companion on a later restart.

The source change preserves the static path for installations without an enabled metamodule. For metamodules, it uses the fabricated provider overlay after boot and keeps the capability XML available during startup when the existing framework mapping preflight passes. Tests cover preparation, command failures, resource verification, capability-pending status, recovery, and startup ordering.

## User-performed KernelSU soft reboot

After the user activated revision 8 with KernelSU soft reboot, NoMount remained enabled and the capability returned `true`. The XML was visible both through `/system/etc/permissions` and the system-server root namespace. The fabricated provider overlay remained enabled and resolved to Google. No translation error or component-failure marker remained. Play Store still resolved to `com.android.vending/.AssetBrowserActivity`. The new system-server PID was 11434; the assistant did not initiate a restart.

The user then reported that holding the bottom navigation area did not invoke translation. The framework `contextual_search` service was absent despite the loaded companion configuration. The installed APK contained a ColorOS-17-only version pattern, and both production module entry points returned before installing hooks on this phone's `V16.1.0`. Coogle was enabled in Vector with SystemUI, launcher, Settings and system in scope, so module selection alone did not explain the missing hooks.

The APK eligibility check now accepts the documented ColorOS 16 and 17 versions. Regression tests exercise the actual package and system-server entry points against system-property fixtures, including display-property fallback and rejection of unsupported versions. Focused platform-dispatch, contextual-search dispatch, and native gesture-listener tests passed. An attempt to run the older combined hook-lifecycle fixture hit a JDK 21.0.2 compiler internal error in `Lower.visitClassDef`; the platform tests run independently of that fixture.

The companion's green status verifies configuration, not end-to-end gesture or translation behavior. Loading the corrected APK hooks into the running system processes and checking the gesture and translation UI remain necessary before claiming the feature is fixed.

The corrected 1.3.9 release APK was installed with `adb install -r`, preserving app data. Its signing certificate matched the previous installation; SHA-256 is `6eadbff1cb5e88890c19666d32e0b93eda94836f72c6cbd181faab5aa58bdcfe`. The embedded companion matches revision 8. After installation, system-server PID remained 11434, the capability remained `true`, and Play Store still had a launcher activity. The assistant did not restart system processes. The corrected hooks still require a user-performed activation before device behavior can be verified.

## ColorOS 16 navigation regression

After the user's next soft reboot, system-server PID was 31195 and `service check contextual_search` reported `found`. The capability, provider and Play Store launcher registration were still correct. The user reported that the expected Circle to Search/translation interaction still did not work.

The earlier `5588334` (`Support ColorOS 16`) commit contained a named `NavigationController` fallback. The later upstream navigation code, including the `a68534d` Launcher 17.5.4 update, no longer contained that fallback; `a68534d` added the renamed/DexKit route to that code. Inspection of the current phone's Launcher confirmed that it still exposes `com.oplus.quickstep.navigation.NavigationController` and its three named gesture predicates. The fallback was restored alongside the existing 17 and renamed routes, using the existing validated `GateBinding` and preserving native setting checks and rollback.

The new ColorOS 16 navigation regression, the existing renamed-navigation regression and platform-dispatch tests passed, and the release build completed. The new APK (SHA-256 `4bece540f54fc6f240f6c7f092f52f676cd602c22d70bf0e3722a4ced6812193`) was installed without clearing data. Only Launcher was reloaded; system-server PID 31195 and SystemUI PID 31966 remained unchanged. The release APK and checksums under `build/releases/live-translation-fix/` were refreshed.

Vector's saved Coogle scope includes Launcher and SystemUI, but their startup logs have not yet confirmed Coogle entry loading. Existing scope entries for these two packages were reapplied through Vector's CLI and Launcher was reloaded again. This does not establish the cause of the remaining device behavior. The screen is dozing/locked, and the user has been asked to unlock it and reproduce the gesture. End-to-end confirmation remains pending; no further reboot was initiated.

## Native ColorOS 16 signature and caller verification

The user still reported no response after the first navigation fallback APK. A raw DEX inspection and a separate reflection-only `app_process` probe on the connected phone confirmed that `OplusHideNavHandleController$INSTANCE.isSupportCircleToSearchHandle()` is **public final and non-static**. The outer controller has a separate static bridge. JADX displays the Kotlin holder with a `Companion` alias, but its raw compiled name is `$INSTANCE`; class naming was not this failure.

The newly restored fallback still passed that inner method through `HookInstaller.booleanMethod`, which accepts static methods only. This deterministically rejects the real ColorOS 16 gate and removes the navigation hooks. The old fixture incorrectly modeled the inner method as static. Updating it to the phone's instance method reproduced the failure before the production fix. The fix accepts a concrete zero-argument boolean gate with either static or instance dispatch and continues to hook the inner method that native callers actually use.

Inspection also confirmed ColorOS 16's named `OplusCuiInputConsumer$mGestureDetector$1` callbacks (`onShowPress`, `onLongPress`, `onScroll`), `Companion.canTriggerCui` and `Companion.startService`, and the controller constructor/static bridge as gate callers. The fallback now deoptimizes those callers, along with the native settings getter that derives a missing Circle to Search setting from the feature gate. It does not change the global EXP flag or overwrite the user's secure settings. The firmware-specific fixture asserts these callers are deoptimized; the independent renamed/static navigation fixture remains separate.

Evidence is under `build/device-debug/2026-11-14/no-action/`, including `device-navigation-preflight.txt`, the red and green navigation runs, and the final build log. The probe initialized no Launcher classes and installed no hooks. No reboot was performed. The phone remained locked/dozing during this investigation, so successful hook setup and visible Circle to Search/live translation still require device verification after the corrected APK is loaded into Launcher.

The final focused navigation, renamed-navigation and platform-dispatch tests passed, and `assembleRelease` completed (`navigation-final-build.log`). APK SHA-256 `9c3bc50b75669fc887f258cabc42b753e6c41c477e164245f4e3f3335386d451` was installed with `adb install -r`, preserving data. The release copy and SHA256SUMS were refreshed; the companion archive is unchanged.

Only Launcher was reloaded (new PID 19069). System-server PID 31195, SystemUI PID 31966 and kernel boot ID remained unchanged. The capability still returned true, the provider remained Google, and Play Store still resolved its launcher activity. The phone was still locked (`NotificationShade`, `mDreamingLockscreen=true`), so no navigation gesture was injected and no end-to-end success is claimed. An unlock/reproduction request remains pending.

## Settings holder failure and verified gesture recovery

Testing on the unlocked home screen reproduced the missing gesture in newly loaded code. A temporary Launcher-only diagnostic module captured the exact installation failure: `NoSuchFieldException: No field INSTANCE in class NavigationSettingsValueProxy`. The native settings holder's raw name is `NavigationSettingsValueProxy$Companion`, with a `Companion` field. This differs from the controller and navigation observer, whose raw `INSTANCE` names are correct. The new caller-deoptimization preflight had confused these names and rolled back all three navigation hooks.

The settings fixture now matches the actual firmware's `Companion` holder and getter. It reproduced the missing-field failure before the fix. Production binds the raw `$Companion` class directly; the ColorOS 16 navigation, separate renamed-navigation, platform eligibility and entry-dispatch tests pass.

Upstream `a10cecc` (v1.3.10, Google Lens 17.75.23 adaptation) was merged into local `main` as `747db3d`. Companion merge conflicts retain the English metadata and local mount safeguards. Both APK and companion now report 1.3.10/versionCode 140, with payload revision 8. A packaging regression verifies that the archive name and companion metadata match the APK version. The upstream Lens binding and cache tests passed in an isolated fixture, avoiding the previously observed aggregate javac crash.

The user performed a KernelSU soft reboot while the merged build was finishing. After it, system-server PID was 8067 and SystemUI PID 8875. The assistant then installed the merged release APK with `adb install -r`, preserving data, and reloaded only Launcher. Diagnostics confirmed three installed navigation hooks, `isSupportCircleToSearchNavbarHidden=true` and `isSupportCircleToSearchHandle=true`. The user reported the feature working.

The diagnostic module `io.github.zopulus.coogle.navprobe` was disabled, uninstalled, and removed from the Launcher process by reloading Launcher. Its cache file was removed. Without the diagnostic module, a 1600 ms hold at the bottom center opened Google's `com.google.android.apps.lens.ContextualSearchEntrypoint`; screenshots show Circle to Search and the Translate button. System-server PID 8067 and SystemUI PID 8875 remained unchanged. No reboot was initiated by the assistant. The provider remains Google, the translation capability returns true, Play Store still resolves `.AssetBrowserActivity`, and wallpaper/color remain visible. This confirms gesture dispatch and availability of the translation entry; the presence of that button alone does not prove translated output.

Evidence is in `build/device-debug/2026-11-14/ready-check/`, including `settings-holder-red.log`, `settings-preflight.txt`, `upstream-live-gates.txt`, `clean-bottom-hold.png`, and `clean-bottom-hold.txt`.

## Merged release artifacts and checks

Artifacts are under `build/releases/1.3.10/`:

- `COS16-Coogle-1.3.10.apk`: SHA-256 `b9613dabacea3fb2bbd6670f3d5d7db669b73965ea4799ba8ae63ece2f52c0ea`.
- `COS16-Coogle-Companion-1.3.10.zip`: SHA-256 `87090be5439dbf6fac634b339fd698530c75aa92d559cd88ab847bf8403f3c8f`.
- `SHA256SUMS` records both files. The APK embeds the exact companion ZIP above.

`python3 package_release.py` now creates these names from the built APK's version. CI uses the same script and publishes tag builds as GitHub prereleases. Only the downloadable filenames changed: the APK still embeds `assets/coogle-companion.zip`, and the in-app installer retains its existing asset and cache paths. The prefixed files have the same bytes as the previously verified packages.

`assembleRelease`, the four focused navigation/platform tests, upstream Lens binding/cache tests, five companion packaging tests, ten native translation tests and four startup tests passed. A broader companion discovery attempt encountered pre-existing Windows-only Bash paths in other fixtures; it is not a passing full-suite run. The older combined hook fixture also retains the documented JDK 21 compiler limitation.

The installed companion remains the already active 1.3.9 payload revision 8; the rebuilt 1.3.10 companion has matching functional scripts and updated version metadata. It was not reinstalled merely for the metadata change. Future Launcher-only hook updates can be tested by reloading Launcher. Changes to hooks in `system_server`, or companion mount/capability changes, require activation through a user-performed KSU soft reboot.
