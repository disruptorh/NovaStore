# Security Model

## Artifact verification (mandatory pipeline, spec §23)

Before ANY install (`DefaultArtifactVerifier`, core:security):

```
Download completed
  → File exists                       (else InvalidPackage)
  → File size check                   (else ChecksumMismatch/InvalidPackage)
  → SHA-256 (streaming, 64 KB windows) (else ChecksumMismatch — install is blocked)
  → APK parsing (PackageVerifier)     (else InvalidPackage)
  → packageName verification          (else InvalidPackage)
  → versionCode verification          (else InvalidPackage)
  → minSdk / architecture             (else IncompatibleDevice)
  → certificate/signature comparison  (else SignatureMismatch — automatic update
                                       is blocked; root does NOT bypass this)
  → Installation
```

## Signature comparison

- The new APK's certificate is extracted on device; the installed version's
  signature is read through `PackageManager` (`GET_SIGNING_CERTIFICATES` on
  API 28+, `GET_SIGNATURES` as the legacy path).
- Incompatible signature → "Different signing certificate. Automatic update
  blocked." The user gets an explanation, and the update never replaces the app
  with someone else's APK.
- If the expected certificate cannot be determined from the source metadata, a safe
  fallback applies: an unconfirmed privileged update is NOT performed.

## Network

- HTTPS only, standard OkHttp certificate validation (modern TLS).
- No custom trust managers, no disabling of hostname verification.

## Sources and installation

- Installs are allowed only from Google Play, F-Droid-compatible repositories
  (the built-in ones, including IzzyOnDroid, and any the user adds) and
  GitHub/GitLab release catalogs.
- APKPure and APKCombo are metadata-only: the catalog stays available to browse
  version history, but files from these mirrors are NOT downloaded and NOT
  installed. This is enforced at the install boundary (`isInstallSourceAllowed`,
  checked in `DownloadUpdateUseCase` and `InstallPackageUseCase`). If Google Play
  can serve a mirror version, the download automatically switches to Play
  (`DownloadUpdateUseCase.prepare`).
- This is a deny-list, not an allow-list: any repository the user adds stays
  installable.

## Root (see [root-installation.md](root-installation.md))

- Root is only an install backend. Root does NOT turn off verification and does not
  bypass signatures (spec §107, §106).
- Commands are built from structured arguments; URLs/metadata from the network NEVER
  reach a shell; `sh -c "<untrusted>"` is forbidden and absent from the code.

## Permissions

| Permission | Reason |
|---|---|
| `INTERNET` | download metadata and APKs |
| `ACCESS_NETWORK_STATE` | Wi-Fi/metered constraints, offline mode |
| `REQUEST_INSTALL_PACKAGES` | standard PackageInstaller flow (API 26+) |
| `POST_NOTIFICATIONS` | update/download notifications (runtime, from 33+) |
| `RECEIVE_BOOT_COMPLETED` | restore the queue after a reboot (spec §69) |
| `QUERY_ALL_PACKAGES` | scan installed apps — the core function of an update manager (official exception for app stores/update managers) |

The list of installed apps never leaves the device: it is used only locally to
compare versions. No source receives the device inventory.

## Privacy

- Installed-app lists are not uploaded to any server (there is no backend of our own).
- Passwords are not stored: Google sign-in goes through Google's own page (WebView
  `EmbeddedSetup`) or the system `AccountManager`; only the issued session token is
  kept on device, and it is encrypted with an Android Keystore key (`SessionCipher`,
  AES-256-GCM). App backup and device-to-device transfer are disabled
  (`allowBackup=false` + `data_extraction_rules`).
- No tokens or secrets in Git; release signing goes through the local environment.
- No analytics; no telemetry.

## What the app does NOT do

- It does not bypass DRM, Play Integrity, licenses, signatures or system prompts.
- It does not delete or modify system components, SELinux or Play Protect.
- It does not run arbitrary commands from a server.
- It does not collect other people's private data.

## Edge cases (spec §68)

Handled: out of space (`InsufficientStorage`), package conflict, signature
mismatch, downgrade (blocked by default), invalid/broken APK, install cancelled by
the user, root denied, package disappearing, network loss, reboot during the queue
(recovers without reinstalling completed tasks).
