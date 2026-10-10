# v2rayEKAi

**Tap. Connect. Protected.**

v2rayEKAi is an Android VPN client for [Xray](https://github.com/XTLS/Xray-core) and [v2fly](https://github.com/v2fly/v2ray-core). It is built for people who just want to open the app, tap once and be online, and it still keeps every advanced option for people who want them.

It is based on [v2rayNG](https://github.com/2dust/v2rayNG) by 2dust, with a new interface, smart server selection and a new identity.

[![Build v2rayEKAi APK](https://github.com/Ahouei/v2rayNG/actions/workflows/build-debug.yml/badge.svg)](https://github.com/Ahouei/v2rayNG/actions/workflows/build-debug.yml)
![Android 7.0+](https://img.shields.io/badge/Android-7.0%2B-0E1726)
![License: GPL-3.0](https://img.shields.io/badge/License-GPL--3.0-0E1726)

---

## Two modes

### Easy mode (default)

For anyone who has never heard of ping, protocols or routing.

- **One button.** Tap to connect, tap again to disconnect.
- **Plain words.** "Not protected" or "You're protected", nothing technical.
- **Choose location.** Tap the server card to see your servers ranked by speed, each with signal bars, a quality word (Excellent, Good, Fair, Poor) and its response time in ms.
- **Fastest.** One tap connects to the quickest server from the latest test.
- **Test again.** Re-tests all servers. The top 30 are ranked and shown 10 at a time.
- **Easy start.** With no server yet, add one by scanning a QR code or pasting a link.

### Pro mode

Everything the original v2rayNG offers: the full server list, subscriptions, routing, DNS, per-app proxy, logs, backup and every core setting.

Switch to Pro with the small **Pro** button at the top of the Easy screen. Return any time with the large **Back to Easy mode** button. Switching never disconnects you.

---

## Download

Release builds are not published yet. Every push and pull request is built by GitHub Actions:

1. Open [Actions → Build v2rayEKAi APK](https://github.com/Ahouei/v2rayNG/actions/workflows/build-debug.yml).
2. Open the latest successful run.
3. Under **Artifacts**, download:
   - **v2rayEKAi-arm64-v8a** for almost every modern phone, or
   - **v2rayEKAi-universal** if you are unsure (larger, works on any device).
4. Unzip it and install `v2rayEKAi_<version>_<abi>.apk`.

These are debug-signed test builds. Android may ask you to allow installs from this source.

---

## Package name

v2rayEKAi uses the application ID `com.ahouei.v2rayekai` (F-Droid build: `com.ahouei.v2rayekai.fdroid`). It installs alongside the original v2rayNG (`com.v2ray.ang`) and does not migrate or read its profiles, settings or geo files; export from v2rayNG and import into v2rayEKAi if needed.

---

## Supported protocols

VLESS (including Reality), VMess, Trojan, Shadowsocks, Hysteria2, WireGuard, SOCKS and HTTP, plus policy groups, proxy chains and custom JSON configurations.

---

## Geo files

- `geoip.dat` and `geosite.dat` are stored in `Android/data/com.ahouei.v2rayekai/files/assets` (the path may differ on some devices).
- The in-app download fetches the enhanced lists from [Loyalsoldier/v2ray-rules-dat](https://github.com/Loyalsoldier/v2ray-rules-dat). It needs a working connection.
- The official [domain list](https://github.com/Loyalsoldier/v2ray-rules-dat) and [IP list](https://github.com/Loyalsoldier/geoip) can also be imported manually.
- Third-party `.dat` files placed in the same folder are supported.

---

## Building from source

The Android project is in the `V2rayNG/` folder and uses Kotlin, Jetpack Compose and Material 3.

1. Clone with submodules:
   ```sh
   git clone --recursive https://github.com/Ahouei/v2rayNG.git
   ```
2. Install the Android SDK (platform 37, build-tools 37.0.0) and the NDK listed in `.github/workflows/build-debug.yml`.
3. Build the tunnel library: `bash compile-hevtun.sh`, then copy `libs/` into `V2rayNG/app/`.
4. Put `libv2ray.aar` in `V2rayNG/app/libs/`. Use the release matching the `AndroidLibXrayLite` submodule from [AndroidLibXrayLite](https://github.com/2dust/AndroidLibXrayLite), or build it yourself with [gomobile](https://pkg.go.dev/golang.org/x/mobile/cmd/gomobile).
5. Build and test from `V2rayNG/`:
   ```sh
   ./gradlew :app:testPlaystoreDebugUnitTest
   ./gradlew :app:assemblePlaystoreDebug
   ```

The CI workflow `.github/workflows/build-debug.yml` runs these exact steps and is the reference setup.

On the Windows Subsystem for Android, grant the VPN permission with:
```sh
appops set com.ahouei.v2rayekai ACTIVATE_VPN allow
```

---

## Credits

- [v2rayNG](https://github.com/2dust/v2rayNG) by 2dust and its contributors: the foundation of this app
- [Xray-core](https://github.com/XTLS/Xray-core) and [v2fly](https://github.com/v2fly/v2ray-core): the proxy cores
- [AndroidLibXrayLite](https://github.com/2dust/AndroidLibXrayLite): the Android core library
- [hev-socks5-tunnel](https://github.com/heiher/hev-socks5-tunnel): the TUN implementation
- [Loyalsoldier](https://github.com/Loyalsoldier): the geo rule lists

---

## License

v2rayEKAi is free software under the [GNU General Public License v3.0](LICENSE), the same license as v2rayNG.
