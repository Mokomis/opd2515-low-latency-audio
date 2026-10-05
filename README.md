# Low-latency audio on the OPPO Pad Mini

ColorOS 16 on the **OPPO Pad Mini OPD2515** refuses Android's low-latency audio paths to almost every app. This repository documents how that block works and gives a small root script that adds apps of your choice to the two lists that control it.

> [!WARNING]
> This changes how the tablet's audio service treats the apps you name. It needs root. The paths it unlocks skip part of the system's sound processing, which OPPO keeps ordinary apps away from, by its own log message, for "performance noise". It was tested with one app on one tablet. Use it at your own risk.

## What ColorOS does

Android offers apps three output paths on this tablet, fastest first:

| Path | How it works | Who gets it on stock ColorOS |
|---|---|---|
| Direct (AAudio MMAP) | The app writes straight to the audio hardware | Apps named in `aaudio-compatible-apps` |
| Ultra low latency ("raw") | Through Android's mixer, without sound processing | Apps named in `ull-compatible-apps` |
| Ordinary fast | The normal shared path | Everyone else |

Both lists live in `/system_ext/etc/Multimedia_Daemon_List.xml`. On the tested firmware the first list has eight entries: four of Google's compatibility-test packages, three latency-measurement apps, and one karaoke app. The second has only the three measurement apps. No game or streaming client is on either.

An app that asks for a low-latency stream and is not listed is turned away twice in a few milliseconds and lands on the ordinary path. The system log shows both refusals:

```text
AAudioService: openStream(...): aaudio denied with imcompatible policy such as peformance noise
AudioPolicyManagerExtImpl: checkUllCompatible() denied raw flag on session 97 for performance noise
```

The checks are plain name lookups. The libraries that make them (`libaaudioserviceextimpl.so` and `libaudiopolicyextimpl.so`) do not measure noise or performance; "performance noise" is the fixed wording of the log line. Why OPPO limits the paths to test and benchmark tools is not documented anywhere I could find.

## What the script does

The list parser (`libmmlistparser.so`) also reads an updatable copy at:

```text
/data/oplus/multimedia/Multimedia_Daemon_Online_List.xml
```

That file does not exist on a stock tablet. `enable.sh` creates it as a full copy of the built-in list, with the packages you name added to both low-latency lists and a newer `<version>` so the parser prefers it. Then it restarts the audio service. The built-in file on the system partition is not touched.

The file must be a complete copy. It holds about a hundred other lists, covering video, camera, recording and volume behaviour, and a partial file would be expected to empty them. The script refuses to write a result that differs from the built-in list by anything other than the version line and the added entries.

## Use

Copy both scripts to the tablet, then from a root shell:

```sh
su -c 'sh enable.sh com.example.game another.example.app'
```

Sound stops for a few seconds while the audio service restarts. No reboot is needed. Each run starts again from the built-in list, so name every app you want each time.

To undo:

```sh
su -c 'sh restore.sh'
```

That deletes the added file and restarts the audio service, which returns the tablet to stock behaviour.

To see what the script would write without installing it:

```sh
su -c 'OUT=/data/local/tmp/preview.xml sh enable.sh com.example.game'
diff /system_ext/etc/Multimedia_Daemon_List.xml /data/local/tmp/preview.xml
```

To see which path an app was given, watch the log while it opens audio:

```sh
logcat | grep -E "aaudio denied|denied raw flag|createTrack_l"
```

## Measured effect

One app, [Punktfunk](https://git.unom.io/unom/punktfunk) (a game-streaming client that asks for a low-latency exclusive stream), on the tablet's own speakers.

| | Stock | With the app listed |
|---|---|---|
| Path given | Ordinary fast | Ultra low latency ("raw") |
| "denied raw flag" in the log | Yes | No |
| Output delay Android reports (`mAfLatency`) | 61 ms | 43 ms |
| Audio behind the picture at rest, as the client measures it | 71–73 ms | 55–63 ms |

The sound was judged the same by ear. The tablet's output did not run dry during play in any session after the change.

## What did not work

**The direct path is still refused.** After the change the log still shows `aaudio denied`, with `getListValueByUid(aaudio-compatible-apps)` returning nothing for the app, even though the same file's second list took effect. The two checks use different lookups. My guess is that the first goes through a part of the system that loads the list only at boot; that was not tested, because the tablet has not been rebooted since the change.

> [!NOTE]
> **Still to be tested, and possibly more to gain.** The direct path is the fastest of the three, so if a reboot (or another way of getting the app onto the first list) opens it, the output delay could fall further than the 43 ms measured here. How much is unknown on this tablet. It is also the path furthest from the stock one, so it is the more likely of the two to sound different. If you try it, the `logcat` line above shows which path the app was given.

## Test status

Tested on one OPPO Pad Mini OPD2515, ColorOS 16 / Android 16, KernelSU, on October 4, 2026:

- The list file made by `enable.sh` was generated on the tablet in preview mode and compared with the one installed by hand: identical apart from the version date.
- Installing, setting ownership and restarting the audio service were done by hand with the same commands the script runs. The script's own install step and `restore.sh` were not run.
- The ultra-low-latency path was granted and used across several sessions the same evening.
- Not tested: a reboot, headphones or Bluetooth, any other app, power draw, any other OPPO, OnePlus or realme device. The file name and list names may differ on other firmware; the script stops if it does not find exactly one of each list.

## How it was found

The client's audio was about 140 ms behind its video. Part of that was the client's own buffer; the rest was a fixed output delay. The system log at the moment the app opened its audio stream showed the two refusals above. The message led to the two libraries, their text strings named the lists, and the list parser's strings named the updatable copy.

## Requirements

- OPPO Pad Mini OPD2515, or a device whose `/system_ext/etc/Multimedia_Daemon_List.xml` contains both lists.
- An unlocked, rooted tablet with a working `su`. Tested with KernelSU.

## Other tools for this tablet

Separate root utilities for the OPPO Pad Mini OPD2515. Each works by itself.

- [Wi-Fi 7 Toggle](https://github.com/Mokomis/WiFi-7-Toggle): enables or restores the tablet's 6 GHz / Wi-Fi 7 band capability.
- [Refresh Manager](https://github.com/Mokomis/opd2515-refresh-manager): lets any app use 144 Hz, or locks an app to 60, 120 or 144 Hz.
- [GPU Clock Floor](https://github.com/Mokomis/adreno-clock-floor): holds the Adreno GPU clock at a chosen minimum, for steadier GPU work such as video decode while streaming.

## License

GPL-3.0-only. See [LICENSE](LICENSE). The scripts copy the device's own list file on the device; no OPPO file is distributed here.
