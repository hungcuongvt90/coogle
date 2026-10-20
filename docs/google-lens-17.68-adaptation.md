# Google Lens Talk / Create compatibility — 2026-10-21

Google 17.68.17 changed the Talk controller constructor from three references, two booleans and a trailing reference to three references, six booleans and a trailing reference. The existing matcher rejected the Talk candidate and returned no eligibility pair, causing both Talk and Create entries to disappear despite the Lens switch being enabled.

The matcher now accepts those two inspected layouts. It retains the unique shared eligibility gate, Create account/gate layout, single mode getter, predicate validation, ambiguity rejection and paired installation rollback. Unknown constructor lengths and non-boolean flags remain rejected. No additional hooks, background work or repeated scans were introduced.

The Lens cache key is now `lens.controllers-v2`; the former `lens` entry is removed so an existing deterministic miss for the unchanged Google APK cannot suppress the corrected scan. The APK identity key and successful/negative result caching continue to apply.

## Verification

- Before the correction, new regression tests failed for both the 17.68 controller layout and the schema-2 negative cache retained by 1.3.4. Logs: `build/lens-diagnostic-2026-10-21/red.log` and `green.log`.
- The Lens structural test is now included in `testHookLifecycle`. Full lifecycle tests passed, including existing disabled-feature, rollback and shortcut boot tests. Final log: `final-regression.log` in the same directory.
- Nine synthetic DEX fixtures passed on Android: old/new diagnostic events, new controller layout, ambiguous anchors/controllers, invalid Create/Talk layouts, unknown Talk layout and missing Talk. The obsolete numeric matcher is still rejected by mutation verification.
- The unchanged production scanner executed against actual Google 17.67.17 and 17.68.17 APKs on the phone. It resolved Create/Talk pairs and 70/68 total targets respectively, replaced a seeded old negative cache and validated subsequent cached resolution. This is APK discovery verification, not a claim to have installed or visually tested the older Google version. Log: `dex-check.log`.
- Release assembly and lint passed: zero errors, unchanged 30 warnings. Log: `check.log`. Final diff review found no blocking correctness, scope, security or performance issue.
- Installed the separate test APK on PLK110 at device time 2026-10-21 03:15:20 and restarted only Google to load the module. Runtime logs confirm Talk/Create hook installation with 66 callers in `:googleapp`, `:interactor` and `:search` (`runtime-reload.log`). No feature switches, scopes or phone reboot were required.
- The user confirmed: **两项均恢复且能打开**.

Test artifact: `Coogle-1.3.4-lens-fix.apk`, 2,886,534 bytes, SHA-256 `69bde0870f746e9d8f99a8a4657165cab26ad36c1aca0854a728de06401b9b26`. Version remains 1.3.4 / 134 for this local test. Signing certificate matches the installed release; the embedded companion ZIP is unchanged. No remote push, tag or release replacement was performed. Future changes to Google's controller structure may require another adaptation.
