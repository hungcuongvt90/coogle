# Compatibility notes and known limitations

The module accepts ColorOS 16 and 17. The 1.3.2 live-translation approach was verified on a real device; Google native eligibility, source-app, scene, network, and overlay-permission checks remain unchanged.

Google Lens and Wallet previews use DexKit to locate obfuscated code. System entry points with stable names continue to use reflection. Results are cached per scoped application APK and rediscovered after updates. Missing or ambiguous targets keep the original behavior; compatibility with every Google App or Wallet version is not guaranteed.

Live translation is declared through a companion-module system-feature XML file and a native provider-resource overlay. Before installation and startup, system `idmap2` checks whether the overlay maps to the current framework without bypassing overlay-policy checks. If it is incompatible, only this module's live-translation declaration is disabled; it can recover on the next startup after compatibility returns. The check cannot guarantee that other overlays will not affect final loading, and it does not remove declarations from other modules. Startup status checks verify both the provider and the system capability without a resident polling loop.

The companion installer checks Google APK discovery and the copy result for every base APK and split APK. Installation stops on failure instead of creating a replacement mount with missing APKs.

The existing power-assistant bridge has a secondary-user limitation: settings reads and observers are not consistently scoped to the current user and may write the primary-user setting into a secondary-user state. Single-user use is unaffected. A multi-user fix requires coordinated changes to settings synchronization and observers and separate validation; this issue was not introduced by 1.3.1.

ColorOS 16 device validation is still required for System UI, launcher, Settings, gesture, camera, Google, Wallet, hotword, and overlay behavior. The release build targets Android API 36 and can be tested on a ColorOS 16 device.
