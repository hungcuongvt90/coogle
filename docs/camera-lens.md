# ColorOS camera Lens integration (experimental)

This integration is opt-in (`camera_lens`) and requires the `com.oplus.camera` LSPosed scope. Restart Camera and Google after changing it. Existing Google Lens eligibility and the companion module are independent.

## Inspected native sources

International CPH2653 / V16.1.0 Camera 6.070.230 and CN PLK110 / V17.0.0 Camera 7.006.125 both retain `key_show_googlelens_icon`, the native menu descriptor, and a presenter click handler that calls `startActivityForResult` with `ACTION_VIEW`, `googleapp://lens`, and the Google app package. The floating-button show/hide code is incomplete in both inspected builds. The CN presenter also returns false for the ordinary photo mode.

DexKit identifies the menu support method using both the option key and `com.oplus.camera.feature.google_lens`, a boolean return type, and one String parameter. The actual APKs each yielded one method (`sj.v2.z4` and `cm.y2.L4` respectively); those names are evidence, not hardcoded targets. Reuse the existing APK-keyed target cache and deoptimize only direct callers.

The hook exposes the retained menu only for unlocked, rear `common` photo mode, outside capture/recording and external image-capture intents. The native click handler remains responsible for launch and statistics.

Google 17.66.11's inspected native Lens gateway checks OEM eligibility using `com.google.lens.feature.CAMERA_INTEGRATION` and `ro.com.google.lens.oem_camera_package`. Both are absent on the tested CN phone. Google-process-only framework hooks expose the feature and append `com.oplus.camera` to the package declaration while the opt-in is enabled. Other features/properties and existing package entries retain their original values. No global system property or companion file is changed.

## Verification

`testHookLifecycle` includes camera mode restrictions and Google OEM declaration callback tests (exact keys, live disable, original values, package preservation, partial-install rollback). All existing regression tasks passed after adding the menu hook. Real APK DexKit discovery was also verified on the connected CN device using both pulled APKs.

Device test: the initial menu hook launched `LensDeeplink`, which immediately finished. After adding the OEM eligibility declaration and restarting Camera/Google, the user confirmed successful Lens UI launch on the CN OnePlus 15.

Future camera builds must retain these anchors and named mode APIs. Ambiguous lookup or incompatible binding leaves original camera behavior intact. Google may change its OEM declaration mechanism in a future update; this implementation does not claim universal update compatibility.
