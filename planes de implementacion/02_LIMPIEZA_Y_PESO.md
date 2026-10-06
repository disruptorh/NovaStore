# P02 — Limpieza de código muerto y reducción de peso

## Objetivo

Quitar peso muerto y activar R8/shrink/ABI para acercar el APK a ≤12 MB (universal) / ≤8 MB por ABI.

## Prerrequisitos

P01-T01 (saber el tamaño inicial).

## Archivos afectados (existen)

- `domain/src/main/java/com/novastore/app/domain/usecase/UninstallAppUseCase.kt`
- `domain/src/main/java/com/novastore/app/domain/usecase/RefreshRepositoriesUseCase.kt`
- `core/datastore/src/main/java/com/novastore/app/core/datastore/SettingsDataStore.kt` (key L95)
- `app/build.gradle.kts` (L44-60, L148-152)
- `core/network/build.gradle.kts` (L36-38)
- `gradle/libs.versions.toml` (retrofit L16, L64-65)
- `run-tests.sh` (raíz)
- `build.md`, `source-providers.md`, `update-engine.md`, `root-installation.md` (raíz; duplicados de `docs/`)
- `app/proguard-rules.pro`
- launcher/branding: `app/src/main/res/drawable-nodpi/`, `docs/branding/icon.png`

## Tareas

### P02-T01 Eliminar UninstallAppUseCase

**Qué:** Borrar el archivo. `grep -r UninstallAppUseCase` debe quedar vacío. Desinstalación sigue por `InstalledAppsRepository.uninstall`.

**Pasos:** grep → delete file → compile.

**Aceptación:** 0 referencias; `:domain:compileReleaseKotlin` OK.

**Verificación:** `rg UninstallAppUseCase` vacío; `./gradlew :domain:compileReleaseKotlin`

**Reversión:** git revert del commit.

### P02-T02 Eliminar RefreshRepositoriesUseCase

**Qué:** Igual. Callers usan `RepositoriesRepository.refreshAll`.

**Aceptación:** 0 refs; compile OK.

**Verificación:** `rg RefreshRepositoriesUseCase`

### P02-T03 Eliminar try_community_dispensers

**Qué:** Quitar `TRY_COMMUNITY_DISPENSERS`, getters/setters en `SettingsDataStore.kt`. Grep key string. No hay UI.

**Aceptación:** key ausente; DataStore compile; settings screen sin regresiones de otras keys.

**Verificación:** `rg try_community_dispensers` vacío; `./gradlew :core:datastore:compileReleaseKotlin`

### P02-T04 Quitar Retrofit

**Qué:** Confirmar `rg 'import retrofit' --glob '*.kt'` = 0. Quitar deps de `app/build.gradle.kts` y `core/network/build.gradle.kts`. Quitar aliases en `libs.versions.toml` si nadie más los usa.

**Aceptación:** build release compile sin Retrofit en el grafo (`./gradlew :app:dependencies --configuration releaseRuntimeClasspath | grep retrofit` vacío).

**Verificación:** ese comando + `assembleRelease` (o `compileReleaseKotlin` de app).

**Riesgo:** uso por reflexión. Mitigación: el grep + compile.

### P02-T05 okhttp-logging

**Qué:** Si `rg HttpLoggingInterceptor` vacío, quitar `libs.okhttp.logging` de `core/network/build.gradle.kts`.

**Aceptación:** compile network OK.

**Verificación:** `rg HttpLoggingInterceptor`; `./gradlew :core:network:compileReleaseKotlin`

### P02-T06 Docs duplicados

**Qué:** Borrar copias en **raíz** `build.md`, `source-providers.md`, `update-engine.md`, `root-installation.md`. Dejar `docs/`. Actualizar `docs/build.md` líneas que apuntan a `scripts/` vs scripts en raíz (hoy `run-tests.sh` está en raíz).

**Aceptación:** un solo ejemplar de cada doc.

**Verificación:** `test ! -f build.md && test -f docs/build.md`

### P02-T07 run-tests.sh

**Qué:** El script hace `cd "$(dirname "$0")/.."` y sale del repo. Cambiar a `cd "$(dirname "$0")"` si el script vive en raíz, o a repo root correcto. Invocar `./gradlew testReleaseUnitTest`.

**Aceptación:** `bash run-tests.sh` encuentra `gradlew`.

**Verificación:** ejecutar; exit 0 o fallos de tests preexistentes (no FileNotFound del wrapper).

### P02-T08 R8 + shrinkResources

**Qué:** En `app/build.gradle.kts` release: `isMinifyEnabled = true`, `isShrinkResources = true`. Completar `app/proguard-rules.pro`: keep Hilt, Worker, `@Serializable`, Room, playapi protobuf/gson stream, JNI `NativeFdroidIndex`.

**Aceptación:** `./gradlew :app:assembleRelease` éxito; APK instala; arranque no crash (si hay device). Tamaño < baseline P01.

**Verificación:** assembleRelease; comparar `scripts/measure-apk.sh`.

**Riesgo:** crash runtime por strip. Reversión: minify false en un commit revert; keep rules iterativas. No avanzar a P12 con crashes.

### P02-T09 ABI splits

**Qué:** `android.splits.abi { isEnable = true; reset(); include("armeabi-v7a","arm64-v8a","x86_64"); isUniversalApk = true }` o `ndk.abiFilters` + universal. El parser C++ en `core/network` justifica splits.

**Aceptación:** outputs por ABI + universal; universal ≤ objetivo o documentar delta.

**Verificación:** `app/build/outputs/apk/release/` listado.

**Riesgo:** CI asume un APK (`NovaStore-vX.apk`). Ajustar `applicationVariants` L88-96 para no pisar nombres.

### P02-T10 Imágenes

**Qué:** Convertir `ic_launcher_art` y PNGs grandes de `docs/branding` usados en APK a WebP lossless. No tocar XML vectors. No meter `docs/screenshots` en APK (verificar que no están en `res/`).

**Aceptación:** `rg` no referencia PNG reemplazado; size res/ baja.

**Verificación:** `find app/src/main/res -name '*.png'`; measure-apk.

### P02-T11 ProGuard keep

**Qué:** Tras primer minify, logcat de crash → reglas. Incluir `-keep class com.novastore.playapi.**`. Mapping en CI artifact no obligatorio.

**Aceptación:** smoke: abrir home, settings, details de una app F-Droid.

**Verificación:** install release; si NO_DEVICE, al menos `assembleRelease` + lint.

## Definición de hecho

Dead use cases y key muertos fuera. Retrofit fuera si inutilizado. Docs únicos. R8 on. Script tests apunta al wrapper. APK medido otra vez.

## Notas de decisión

No borrar `AppCardGrid`/`SeeAllLink` aquí: el scan de “0 refs” ignora llamadas Compose en el mismo archivo; se decide en P07-T05.

No borrar `core/playapi`: es el catálogo Play vivo.
