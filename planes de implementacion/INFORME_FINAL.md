# INFORME_FINAL — NovaStore v7.3.0

Fecha: 2026-10-08 · Git SHA (código): `44ed949` · SHA cierre de docs: `1446bf6`

## Resumen

Cierre de las 12 fases (P01–P12; P10 omitido por decisión de identidad). APK final
**6.04 MB universal** (−65.9 % frente a baseline), splits ABI ≤ 5.23 MB, minify+shrink
activos, 147 tests unitarios verdes, lint vital y `assembleRelease` exit 0.

## Métricas before / after

| Métrica | Before (P01, `53163e3`) | After (P12, `44ed949`) |
|---|---|---|
| APK universal | 18 581 405 B | **6 337 308 B** |
| Cold start | 376 ms | **392 ms** |
| Time to home contenido (warm) | 3 458 ms | **3 190 ms** |
| RSS / PSS idle | 270 380 / 212 500 KB | **195 220 / 133 120 KB** |
| Jank home scroll | 6.64 % | **5.99 %** |

Detalle completo en `METRICAS_BASE.md` (sección P12-T01).

## applicationId y perfil

- `applicationId = "com.novastore.fork"` (no cambia).
- Nombre visible, iconos, User-Agent y prefijo `NovaStore*` **sin** renombrar (Req 4).
- `versionName = "7.3.0"` / `versionCode = 15`.

## Mirrors ausentes (comando final del criterio I)

```
$ rg -i 'apkpure|apkcombo' --glob '*.kt' --glob '*.xml'
./core/model/src/test/.../StoreLinksTest.kt:   (test de parse de URL de usuario)
./core/model/.../StoreLinks.kt:                (parse de URL de usuario + comentario)
```

Únicamente el parse `StoreLinks` y su test — 0 clientes HTTP a hosts mirror.

## Checklist 99

### A. Proceso
- [x] `PROGRESO.md` sin tareas `- [ ]` salvo bloqueos documentados.
- [x] Commits convencionales citando IDs (P01…P12; P10 omitido).
- [x] `./gradlew :app:assembleRelease testReleaseUnitTest lintVitalRelease` **exit 0** (BUILD SUCCESSFUL).

### B. Req 1 — Calidad y velocidad
- [x] `isMinifyEnabled = true` + `isShrinkResources = true` (release en `app/build.gradle.kts`).
- [x] Splits ABI: arm64-v8a / armeabi-v7a / x86_64 + universal (`app/build/outputs/apk/release/`).
- [x] APK after < baseline (6 337 308 < 18 581 405).
- [x] Room schema v6 ≥ 5 (`core/database/.../NovaDatabase.kt`).
- [x] OkHttp `Cache(File(context.cacheDir, HTTP_CACHE_DIR), 50 MiB)` (`core/network/.../NetworkModule.kt:36`).
- [x] Home/Search/Category paginados con límite (P03-T04/T05/T06).
- [x] Lazy lists de apps con `key` (P03-T07).
- [x] Pantallas P08-T09 con empty/error/loading (P08).

### C. Req 2 — Updates sin mirrors
- [x] `rg -i 'ApkPureClient|ApkComboClient'` = 0.
- [x] `rg mirrorVersionsFor` = 0; `rg 'Stage.MIRRORS'` = 0.
- [x] Settings sin APKPure/APKCombo (P05-T05).
- [x] Scan itera solo `enabledProviders` (P05-T06, `CatalogDao.getVersionsFor` filtra repos habilitados) + pase Play si toggle (P05-T08); `UpdateEngineScanTest` documenta scan catalog-driven.
- [x] `PeriodicWorkRequestBuilder` (`app/work/UpdateScanWorker.kt:108`) + `BootReceiver` re-arma scan (P05-T09).
- [x] Canales `INSTALLATION`/`ERRORS` usados vía `NotificationChannelIds` (P05-T10).

### D. Req 3 — Fuentes
- [x] `AppSourceProvider` + 4 impls: `FdroidIndexSourceProvider`, `GitHubReleaseSourceProvider`, `GiteaCompatibleSourceProvider`, `HtmlRegexSourceProvider`.
- [x] `docs/source-providers.md` cita clases existentes (P04-T09).
- [x] UI add/edit/enable/reorder/delete+confirm (P07).
- [x] `ensureBuiltIns` no pisa `priority` (P06).
- [x] Import/export JSON roundtrip test (`data/.../SourceBackupTest.kt`).
- [x] Preferred source por app.
- [x] Dedup catálogo alineada: `SourceResolutionPolicy` prioridad → versionCode → id; `UpdateEngine` usa prioridades.

### E. Req 4 — Identidad
- [x] Nombres `NovaStore*`, iconos, User-Agent intactos; `applicationId = com.novastore.fork`.

### F. Req 5 — Diseño
- [x] Tokens Spacing/Shape/Color en `core/ui/theme`.
- [x] `AccentPalette` = 6 entradas (`AccentPalettes`, theme/Color.kt).
- [x] Temas light/dark/AMOLED/dynamic.
- [x] 0 `Color(0x` en Home/Search/Details/Downloads/Updates/Installed/Settings Screens: **0 hits** (`rg 'Color\\(0x' feature --glob '*Screen.kt'` → vacío). P12 tokenizó los 4 de Details (`StarAmber`, `Favorite`, `BrandIndigo`).

### G. Req 6 — Settings
- [x] 8 secciones (P09): Fuentes, Actualizaciones, Descargas e instalación, Apariencia, Almacenamiento y caché, Privacidad, Copia de seguridad, Acerca de.
- [x] Búsqueda de ajustes (`SettingsSearch.kt` + test).
- [x] Confirmación en borrados.
- [x] `SettingsScreen.kt` = 250 L (≤ 250).

### H. Seguridad y a11y
- [x] Descarga HTTP rechazada (`DownloadUrlPolicy.isSecureDownloadUrl`; P11-T03).
- [x] `network_security_config` cleartext false + `usesCleartextTraffic="false"` (P11-T03).
- [x] SHA-256 de índice F-Droid mandatory; auto-update no instala sin hash (`UpdateAllUseCase.autoUpdatable` exige sha256 válido; P11-T01). HTML sin checksum de origen → solo manual (P05/P06 + warning).
- [x] Firma: no skip si digest instalado null (`DefaultArtifactVerifier` bloquea installed-desconocido; `anySignerMatches` todos los signers; P11-T02).
- [x] Session plaintext migrado (`SessionCipher.migrate` + `SessionMigrations`; P11-T04).
- [x] Targets 48 dp audit UI (P02/P08).
- [x] `MANAGE_EXTERNAL_STORAGE` ausente (P11-T09); storage solo `cache/downloads`.

### I. Mirrors residual
- [x] Comando final: solo `StoreLinks` + test (ver arriba).

### J. Entregable
- [x] `planes de implementacion/INFORME_FINAL.md` con métricas before/after y SHA.

## Riesgos residuales

- **SHA-256 opcional en Play / sin pinning**: los candidatos de Google Play llevan
  `sha256 = null`; el verificador calcula el hash local y el flag auto-update exige
  checksum válido → **Play no auto-instala** (cambio de comportamiento documentado en
  P11). No hay certificate pinning de red: se confía en la CA del sistema + verificación
  de firma APK contra signer conocido; la cadena de confianza de fuentes se audita con
  `SignatureVerifier`/`DefaultArtifactVerifier`.
- **Fuentes HTML sin checksum de origen**: hash calculado post-descarga, sin digest de
  origen para comparar (warning `HTML_INTEGRITY_WARNING`).
- **Jank 5.99 %** > objetivo 5 % (mejora vs 6.64 % baseline; no es criterio de cierre).
- **Cold start +16 ms** vs baseline (dentro del ruido de build ±0.09 % no aplica a
  tiempos; +4 %, sin regresión funcional).
- Sin GitHub Release publicado por el agente (APK local descrito en P12; el usuario
  puede publicarlo con `gh release create v7.3.0`).

## Cómo medir (repro)

- `bash scripts/measure-apk.sh app/build/outputs/apk/release/NovaStore-v7.3.0.apk`
- Cold start: `adb shell am force-stop com.novastore.fork; sleep 3; adb shell am start -W -n com.novastore.fork/com.novastore.app.MainActivity` (3 tandas, mediana).
- Contenido (proxy estabilidad): captura `screencap` cada 250 ms tras `am start` hasta 2 frames iguales.
- RAM: `adb shell dumpsys meminfo com.novastore.fork` (bloque App Summary).
- Jank: `gfxinfo ... reset`, 5 swipes 540 1900→540 700, `dumpsys gfxinfo`.
- Suite: `./gradlew :app:assembleRelease testReleaseUnitTest lintVitalRelease`.