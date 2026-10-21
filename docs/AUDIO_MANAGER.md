# OPD2515 Audio Manager (0.1 test)

Root Android app for OPPO Pad Mini OPD2515, ColorOS 16 / Android 16. This adds a searchable launcher-app list with icons, names, packages and independent eligibility switches. It uses the same two lists described in the original research. It does not force an audio path globally. An app must request and support the path itself.

## Install and use

1. Install the debug-signed test APK. Android/ColorOS may require unlocking and approving USB installation.
2. Open KernelSU → Superuser and enable root for **OPD2515 Audio Manager**. KernelSU can hide `su` entirely until this is enabled; the app may report “No such file or directory.” Return to the app and tap Reload.
3. Search for a launchable app. ON means the package has attribute `0` in **both** `aaudio-compatible-apps` and `ull-compatible-apps`. A partial entry is labeled. ON normalizes the selected package to attribute `0` in both lists; OFF removes that package from both lists, including any stock entry for that package.
4. Confirm each change. Sound briefly stops while audioserver restarts. Reopen the client audio session. Raw eligibility applies after that restart. MMAP eligibility changes, including removals, need a later manual reboot. A checked switch indicates policy eligibility, not proof that the client is using MMAP or raw.

Every edit reads the entire active override (or stock if no override exists), preserves the other package entries and policy sections, and increments the version above both source versions. XML is parsed and validated; unexpected list structure, duplicates, entity/DOCTYPE declarations and invalid package names stop the operation. The serializer may change XML formatting. `/system_ext` is never written.

## Ownership, backup and recovery

The first confirmed write adopts the existing override and saves its exact bytes as:

`/data/oplus/multimedia/opd2515-audio-manager/original.xml`

If there was no override, an `original-absent` marker is recorded instead. This state directory is root-owned, mode 0700. It survives app uninstall. Do not delete it casually: it contains the baseline and expected hash.

A root-side directory lock serializes manager writes. Current override and stock SHA-256 values are checked again under the lock. After adoption, a change from another tool blocks subsequent writes, even after Reload. Firmware stock-policy changes also block writes. Do not work around this by deleting backups; review and reconcile the files first. Other tools do not honor this manager's lock, so avoid editing the policy concurrently.

The candidate is decoded into a staging directory on the same filesystem, checked against its SHA-256, given system:system ownership, 0644 permissions and `u:object_r:oplus_multimedia_file:s0`, then renamed into place. The previous file is retained until audioserver restarts with a new PID. A normal failure after replacement restores the previous file and expected hash and attempts another audio restart. Abrupt process death/power loss can leave a lock or incomplete transaction marker; the manager then refuses further writes. This is fail-closed protection, not a power-loss-proof database. Keep the backup and transaction directory for manual recovery.

**Restore pre-manager backup** restores the original override byte-for-byte, subject to conflict checks. **Restore Stock** removes the override only if none existed before the manager. It deliberately refuses on a tablet that already had a Punktfunk/custom override. Both actions require confirmation and restart audio. The manager never reboots the tablet.

## Build and local tests

Requires JDK 17, Android SDK platform 36, and network access for Gradle/Android build dependencies:

```sh
./gradlew assembleDebug lintDebug
mkdir -p /tmp/opd2515-policy-tests
javac -d /tmp/opd2515-policy-tests app/src/main/java/dev/opd2515/audiomanager/Policy.java tests/PolicyTest.java
java -cp /tmp/opd2515-policy-tests dev.opd2515.audiomanager.PolicyTest
```

APK: `app/build/outputs/apk/debug/app-debug.apk`. It is a development-signed build, not a production release. Do not commit signing keys, SDK paths, device policy files, installed-app inventories or logs. The manifest has no requested permissions, no network permission and scoped launcher package visibility. There is no analytics or network code.

## Device verification

Before any test, privately back up stock and online policy and record owner/mode/SELinux label. Test ON for two launchable apps, OFF for one, and confirm the other and all pre-existing entries remain. Restore the pre-manager baseline and compare SHA-256. Check failure behavior with a stale expected hash. Do not remove or alter existing Punktfunk entries for a temporary test.

Inspect `dumpsys media.aaudio`, `dumpsys media.audio_flinger`, and relevant `logcat` lines while a client opens an audio stream. Policy readback and an audioserver restart do not prove that a client used a low-latency stream. MMAP verification requires a manual reboot and an active compatible client session. The repository's historical 61→43 ms observation remains historical; this app does not establish a new latency measurement.
