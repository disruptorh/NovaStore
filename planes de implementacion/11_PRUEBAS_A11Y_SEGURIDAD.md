# P11 — Pruebas, accesibilidad y seguridad

## Objetivo

Integridad de APK real, tests del motor/fuentes, a11y AA, CI que corre lo que dice.

## Prerrequisitos

P05, P06, P09. P02-T07 (script tests).

## Archivos afectados

- `core/security/DefaultArtifactVerifier.kt` L42-47, L73-77
- `core/security/SignatureVerifier.kt`
- `core/security/SessionCipher.kt`
- `core/downloader/DownloadEngine.kt` (~L340 URL)
- `app/src/main/AndroidManifest.xml` + crear `app/src/main/res/xml/network_security_config.xml`
- `core/installer/RootCommandExecutor.kt`
- `data/source/*`, `data/updater/*`
- `.github/workflows/build.yml`
- `run-tests.sh`
- features a11y (resto P08)

## Tareas

### P11-T01 SHA-256

**Qué:** Si el provider **ofreció** sha256 y no coincide → Invalid. Si **no** ofreció: `VerificationResult.Valid` con flag `checksumFromSource=false` **o** nuevo estado `UnverifiedChecksum` que **bloquea auto-update** y exige tap en UI. Prescrito: HTML provider y Play: sin hash de origen → calcular hash, mostrar en UI, permitir install manual, **prohibir** auto-install (`UpdateAllUseCase` filtra checksumFromSource!=true salvo Play firmado por session). F-Droid con sha256: mandatory match.

**Aceptación:** test: expected null + autoUpdate → skip. expected wrong → Invalid.

**Verificación:** tests security + UpdateAll.

**Riesgo:** Play deja de auto-actualizar. Aceptable.

### P11-T02 Firma

**Qué:** Si app instalada y `installedCertDigest == null`, **no** instalar (Invalid), no skip. Comparar todos `apkContentsSigners` (any match) no solo `.first()`. Documentar rotación: no soportada; mismatch = bloqueo.

**Aceptación:** tests Robolectric existentes ampliados.

**Verificación:** `:core:security:testReleaseUnitTest`

### P11-T03 HTTPS downloads + NSC

**Qué:** `DownloadEngine` aborta si `url.scheme != https`. `android:networkSecurityConfig` `cleartextTrafficPermitted=false`. Deep links http Play: upgrade a https en `StoreLinks.parse`.

**Aceptación:** test engine http → error. Manifest apunta al xml.

**Verificación:** rg usesCleartextTraffic; file xml.

### P11-T04 SessionCipher migrate

**Qué:** `decrypt` si plaintext: encrypt+rewrite DataStore (Account + Play session). Una pasada en `NovaStoreApp` onCreate IO.

**Aceptación:** test cipher: plaintext in → ciphertext stored (robolectric o jvm si se abstrae).

**Verificación:** tests + code path.

### P11-T05 RootCommand tests

**Qué:** JUnit: argumentos ilegales rechazados; path fuera de `cache/downloads` rechazado.

**Aceptación:** ≥3 tests.

**Verificación:** gradle.

### P11-T06 Tests providers y scan

**Qué:** Cubrir P04/P05 gaps: registry enabled, html regex, update scan no llama mirrors (mocks).

**Aceptación:** `testReleaseUnitTest` green. Contar tests > 35 actuales.

**Verificación:** `./gradlew testReleaseUnitTest`

### P11-T07 CI

**Qué:** `.github/workflows/build.yml` sigue unit tests; añadir `run-tests.sh` path correcto. Instrumented **no** exigidos en CI (no emulator). Documentar `connectedAndroidTest` local.

**Aceptación:** YAML `./gradlew testReleaseUnitTest`; script no `cd ..` malo.

**Verificación:** leer yaml + bash -n run-tests.sh.

### P11-T08 A11y RTL

**Qué:** Icons `AutoMirrored` en chevrons. `padding(horizontal)` no start/end mixto mal. TalkBack: tabs `contentDescription`. fontScale 1.3 no recorta botones (soft).

**Aceptación:** conteo AutoMirrored > unmirrored para actions de navegación. Contraste P07.

**Verificación:** rg Icons.AutoMirrored vs Icons.Filled.Chevron.

### P11-T09 Permisos storage

**Qué:** Evaluar `MANAGE_EXTERNAL_STORAGE`: si descargas viven en `cache/downloads` (DownloadEngine), quitar all-files y AllFilesAccessRow. Conservar `REQUEST_INSTALL_PACKAGES`. `WRITE_EXTERNAL_STORAGE` maxSdk 28: quitar si no hay path legado.

**Aceptación:** Manifest mínimo; Settings sin fila all-files si se quitó el permiso.

**Verificación:** lectura Manifest; compile.

**Riesgo:** usuarios que guardan APK en Downloads público. Prescrito: share/`ACTION_CREATE_DOCUMENT` para export APK, no all-files.

## Definición de hecho

Verifier más estricto, https only, cipher migrate, tests+CI, permisos recortados, a11y gaps del audit tocados.

## Notas de decisión

No certificate pinning (rompe MITM debug y CAs de repos). No connectedAndroidTest en CI sin runner.
