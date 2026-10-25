# Coogle

[English](README.md) | [简体中文](README.zh-CN.md)

Make Chinese-market ColorOS more Google-like. Supports ColorOS 16 and 17.

## Features

- **Hey Google**: restore Google voice activation on Chinese-market firmware.
- **Google Lens**: restore the Google Lens entry points.
- **System navigation**: restore navigation options in Settings, Circle to Search, and the assistant gesture from the lower corners.
- **Google search bar**: enable the Google search bar at the bottom of the launcher.
- **Google Wallet**: open the Google Wallet card by double-pressing the power button; set Google Wallet as the default payment app.
- **Lock-screen shortcuts**: restore Google Assistant, Wallet, QR scanner, and home-control shortcuts.
- **Control Center**: restore the native Wallet and QR scanner entries.
- **GMS restrictions**: remove the China-region GMS restriction markers.
- **Settings Google entry**: show Google services at the bottom of Settings.

## Installation

Requirements: a rooted device (KernelSU), an LSPosed-compatible framework (LSPosed, Vector, or another libxposed implementation), and ColorOS 16 or 17.

1. Install the release APK. The APK embeds the companion module, so no separate download is needed.
2. Enable Coogle in the framework manager and approve the scope requests.
3. Open Coogle and grant it root in KernelSU; confirm the companion module installation from the status card.
4. Reboot once so the companion payloads (Google systemization, hotword components, live-translation overlay) are mounted, then enable the features you want.

## Coogle companion module

The companion module combines Google system-app systemization, Hotword enrollment components, software hotword detection, and Circle to Search live-translation configuration.

The companion module is checked automatically when Hey Google is enabled.

Google systemization and Circle to Search live-translation configuration are enabled by default. The voice components follow the APK switch and use software hotword detection. The DSP wake-word audio patch is removed because it conflicts with the vendor location HAL on affected ColorOS 16 firmware. After Hey Google is disabled, rebooting stops the voice-enrollment mounts and skips model initialization. Voice support is disabled by default on first install or upgrade from an older menu version so initialization cannot run before the APK switch is synchronized.

## Building

See [BUILDING.md](BUILDING.md). GitHub Actions builds the release APK with the embedded companion module on every push and publishes it on version tags; see the [Releases](https://github.com/zopulus/coogle/releases) page.

## Credits

Some code is based on [ONextBox](https://github.com/MiToverG422/ONextBox) and [LuckyTool](https://github.com/luckyzyx/LuckyTool).

The UI is inspired by [KernelSU](https://github.com/tiann/KernelSU) and [LSPosed](https://github.com/LSPosed/LSPosed).

Thanks to the developers and contributors of these projects.

Author: **zopulus**
License: **GPL-3.0**
