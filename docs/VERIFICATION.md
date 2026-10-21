# Implementation verification — 2026-10-19

Development build: 0.1-test. This is a test APK, not a production release.

- JDK 17 / Android SDK 36: `assembleDebug` and `lintDebug` passed locally.
- JVM policy tests passed: multiple selections; isolated OFF; preservation of unrelated XML content; version increment above stock; XML escaping; invalid package rejection; malformed lists, duplicate version and DTD rejection.
- Connected OPD2515, Android 16, ColorOS V16.1.0, KernelSU root confirmed.
- The same Java policy editor and root transaction writer were exercised against the real device using an ADB transport shim (host-only Android Base64 adapter; production shell transaction unchanged except explicit failure injection).
- Device integration passed: two ON selections, isolated OFF, both existing Punktfunk packages retained, stale-hash rejection, refusal to remove a pre-existing override through Restore Stock, exact pre-manager baseline restoration.
- Forced failure after replacement exercised rollback; exact previous policy bytes were restored. This exposed a restart timing race, fixed by waiting for service availability and a new PID instead of assuming a two-second restart.
- An initial large-argument write failed before replacement; full XML now transfers through a base64 heredoc rather than a command argument.
- Debug APK installed successfully. KernelSU required manually enabling root for the new app. App loaded the current override and showed the searchable launcher-app list.
- In the installed app, Calculator ON and OFF were confirmed through the UI and independently read back from both policy lists; both existing Punktfunk entries remained. The app’s Restore pre-manager backup action was also exercised. A stale UI snapshot safely refused a write; root subprocess errors are now collected even when the input pipe closes early.

No reboot was performed. No active MMAP endpoint was present in the captured AAudio dump, so actual MMAP/raw client selection and latency improvements have not been revalidated. Sound interruption and policy writes were tested; post-reboot MMAP behavior remains the historical research result.

Private policy backups, device identifiers, app inventories, screenshots, logs, SDK paths and signing keys are excluded from this repository. A targeted source scan found no common GitHub/AWS credential patterns, private key blocks, local user paths or connected-device identifier. This is a scoped change scan, not an audit of all repositories or all history.
