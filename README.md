# Derafsh VPN 🚩 | درفش

**Derafsh** (درفش) is a simple, fast and ad-supported VPN client for Android — built for people who just want a **one-tap connection** to the fastest available server, without any technical complexity.

> Derafsh (درفش کاویانی) — the legendary flag of freedom.

## ✨ Features

- 🖐️ **One-tap connect** — press the big button, we do the rest
- ⚡ **Auto fastest server** — all configs are tested automatically, the app connects to the fastest one
- 🔄 **Auto-updating configs** — free configuration lists are fetched from GitHub and kept fresh automatically
- 🌍 **Bilingual** — English & فارسی
- 📺 **Ad-supported** — free to use, supported by ads

## 🧱 Tech Stack

- **Core**: [Xray-core](https://github.com/XTLS/Xray-core) via [AndroidLibXrayLite](https://github.com/2dust/AndroidLibXrayLite)
- **UI**: Kotlin + Jetpack Compose (Material 3)
- **Protocols**: VMess, VLESS (Reality), Trojan, Shadowsocks

## 📥 Download

Grab the latest APK from [Releases](https://github.com/CheginiSoroush/Derafsh-Vpn/releases) or build it yourself with GitHub Actions (`Actions → Build Derafsh APK`).

## 🛠️ Build

```bash
git clone --recurse-submodules https://github.com/CheginiSoroush/Derafsh-Vpn.git
cd Derafsh-Vpn
# open the V2rayNG/ folder in Android Studio, or:
cd V2rayNG && ./gradlew assembleRelease
```

CI builds are signed automatically if you provide the `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD` repository secrets to sign with your own release key; without them the workflow still succeeds and produces an **unsigned** release APK.

## 📄 License & Attribution

This project is licensed under the **GNU General Public License v3.0** — see [LICENSE](LICENSE).

Derafsh is based on [v2rayNG](https://github.com/2dust/v2rayNG) by 2dust, licensed under GPL-3.0. All modifications are documented in this repository and released under the same license.
