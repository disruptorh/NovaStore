<div align="center">

<img src="docs/branding/icon-256.png" width="128" alt="Nova Store icon"/>

# Nova Store

**One store for every source — Google Play, F-Droid, IzzyOnDroid and more.**<br/>
Install and update all your apps from one place, with or without a Google account, with or without Google services.

[![Build](https://github.com/disruptorh/NovaStore/actions/workflows/build.yml/badge.svg)](https://github.com/disruptorh/NovaStore/actions/workflows/build.yml)
[![Release](https://img.shields.io/github/v/release/disruptorh/NovaStore?color=6965F1&label=release)](https://github.com/disruptorh/NovaStore/releases/latest)
[![License: GPL v3](https://img.shields.io/badge/license-GPLv3-A556F7.svg)](LICENSE)
![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-Compose-7F52FF?logo=kotlin&logoColor=white)

[**⬇ Download the latest APK**](https://github.com/disruptorh/NovaStore/releases/latest) &nbsp;·&nbsp; [**❤ Sponsor the original author**](https://github.com/sponsors/Vincentdiligent)

</div>

---

> **This is a personal fork of [Nova Store](https://github.com/Vincentdiligent/NovaStore).**
> The original project and all credit belong to its author, [@Vincentdiligent](https://github.com/Vincentdiligent).
> This fork is maintained at [disruptorh/NovaStore](https://github.com/disruptorh/NovaStore), ships
> under its own application id (`com.novastore.fork`) and signing key, and can be installed next to
> the original app.

## What this fork changes

- **Encrypted sessions.** Play and anonymous session tokens are encrypted at rest with the Android
  Keystore (AES-GCM), and cloud backup and device-to-device transfer are disabled.
- **Mandatory signature verification.** Every APK must pass signature verification before install;
  unsigned or unverifiable packages are rejected.
- **Metadata-only mirrors.** APKPure and APKCombo are shown for reference only. Apps are installed
  and updated from Google Play, F-Droid repositories (including IzzyOnDroid) and any repository you
  add yourself.
- **Independent identity.** Ships as `com.novastore.fork`, signed with its own key, so this fork and
  the upstream app can be installed side by side.

<p align="center">
  <img src="docs/screenshots/home.png" width="200" alt="Home"/>
  &nbsp;
  <img src="docs/screenshots/details.png" width="200" alt="App details"/>
  &nbsp;
  <img src="docs/screenshots/updates.png" width="200" alt="Updates"/>
  &nbsp;
  <img src="docs/screenshots/search.png" width="200" alt="Search"/>
</p>

## Why Nova Store

Most stores make you choose: Play *or* open-source repositories, an account *or* privacy.
Nova Store merges them into one catalog and one update list, and treats every source as independent.
If one source is slow or down, the others keep working.

## Features

### 🛍 Every source in one catalog
- **Google Play** with an anonymous session (no account), or sign in with your Google account for purchased apps.
- Works **without Google Play Services**, with microG, or with full GMS.
- **F-Droid, IzzyOnDroid** and any F-Droid-compatible repository. GitHub and GitLab releases also appear in search.
- One search across all sources at once, with source filters and progressive results.

### 🔄 Updates that are actually right
- Version codes, signatures and version names are compared together, so you never see fake updates like `11.0.3 → 11.0.3`.
- Choose the source for each app ("Update from…") and filter the list by source.
- **Update all** downloads in parallel and installs in order.
- Background checks with notifications. Modified, foreign-signed and paid apps are flagged clearly instead of failing.
- Ignore an app or a single version; ignored apps get their own screen.

### ✨ A store that feels good
- Premium home screen: recommendations, Play charts, rows by category with **See all**, and a "New on F-Droid" row.
- Rich app pages: screenshots, reviews, changelog, permissions, dependencies and developer contacts. Add apps to favorites.
- **QR scanner**: scan a Play, F-Droid or any store link and Nova opens the app page.
- Store links (`market://`, `play.google.com`, `f-droid.org`, shared links) open in Nova.
- Home layouts (rows, grid, list), 15 accent colors, dark and light themes, and a collapsing or pinned search bar.
- Repository loading progress on the home screen and in a notification.
- Languages: English, Русский, Español, Français.

### 🔒 Safe installs
Every APK must pass signature verification before install — unsigned or unverifiable packages are rejected. Size, SHA-256, package name, version and ABI are also checked against the installed app.
Installs use the standard Android installer by default. Root and Device Owner backends are optional.

## Install

1. Download `NovaStore-vX.Y.Z.apk` from [Releases](https://github.com/disruptorh/NovaStore/releases/latest).
2. Open it and allow "Install unknown apps" for your browser or file manager.
3. Launch Nova Store. Google Play works immediately with no account needed.

**Requirements:** Android 8.0 (API 26) or newer.

## Build from source

```bash
git clone https://github.com/disruptorh/NovaStore.git
cd NovaStore
./gradlew assembleRelease
# → app/build/outputs/apk/release/app-release.apk
```

You need JDK 17 and the Android SDK (API 35).

To sign with your own key, add these to `keystore.properties` (or `local.properties`):

```properties
storeFile=/path/to/keystore.jks
storePassword=…
keyAlias=…
keyPassword=…
```

Without a key, the build is signed with the local debug key.

### CI and releases
[GitHub Actions](.github/workflows/build.yml) builds the release APK and runs the unit tests on every push.
Each build's APK is available as a workflow artifact.
Pushing a `v*` tag publishes a GitHub Release with the APK and `SHA256SUMS.txt`.

Cut a release with the helper script — it bumps `versionCode` (+1) and the patch version in `app/build.gradle.kts`, updates the tag example in this README, commits, tags and pushes; CI then publishes the release:

```bash
./release.sh
```

It refuses to run with a dirty working tree or with no code changes since the last tag. You can also tag manually:

```bash
git tag v7.2.3 && git push origin v7.2.3
```

To sign CI builds with your release key, add these repository secrets:

| Secret | Value |
|---|---|
| `NOVA_KEYSTORE_BASE64` | `base64 -w0 keystore.jks` |
| `NOVA_STORE_PASSWORD` | keystore password |
| `NOVA_KEY_ALIAS` | key alias |
| `NOVA_KEY_PASSWORD` | key password |

## Tech stack

Kotlin · Jetpack Compose (Material 3) · Hilt · Room · DataStore · WorkManager · OkHttp · Protobuf · ZXing

The project uses a modular, clean architecture:
- `app`
- `core/*` (model, network, database, datastore, installer, updater, ui, playapi)
- `data`
- `domain`
- `feature/*` (home, search, details, updates, installed, downloads, settings, account)

Further documentation:
- [docs/update-engine.md](docs/update-engine.md)
- [docs/source-providers.md](docs/source-providers.md)
- [docs/security.md](docs/security.md)
- [docs/root-installation.md](docs/root-installation.md)
- [CHANGELOG.md](CHANGELOG.md)

## Support the original project

Nova Store is free, has no ads and no tracking, and is built in spare time.
If it saves you time, please support the original author, [@Vincentdiligent](https://github.com/Vincentdiligent):

[![Sponsor on GitHub](https://img.shields.io/badge/Sponsor-GitHub-EA4AAA?logo=githubsponsors&logoColor=white)](https://github.com/sponsors/Vincentdiligent)

Stars ⭐, bug reports and translations help too.

## Disclaimer

Nova Store is an independent project and is not affiliated with Google or F-Droid.
Google Play is a trademark of Google LLC.
Apps are downloaded from their original sources, and their licenses and terms apply.

## Credits

Nova Store was created by [@Vincentdiligent](https://github.com/Vincentdiligent) and its contributors:
https://github.com/Vincentdiligent/NovaStore. This fork is maintained by
[@disruptorh](https://github.com/disruptorh).

## License

Nova Store is free software, licensed under the [GNU General Public License v3.0](LICENSE).
This fork keeps the same license as the original project.
