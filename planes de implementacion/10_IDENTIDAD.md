# P10 — Renombrado e identidad

## Objetivo

Nueva identidad de producto sin destruir datos de usuarios que ya tienen `applicationId` `com.novastore.fork`.

## Nombres candidatos

| Nombre | Por qué | Riesgo de marca |
|---|---|---|
| **Arkiv** | Catálogo/archivo de APKs; 5 letras; no es “store”. | Bajo (Arkiv en sueco = archivo; no hay tienda Android masiva). |
| **Fount** | Fuente/manantial de repos. | Medio-bajo (apps wellness “Fount”). |
| **Origo** | Origen de la app instalada. | Bajo. |
| **Cairn** | Mojón: marcas el origen de cada paquete. | Bajo. |
| **Relic** | Artefacto versionado. | Medio (Relic APM). |

**Recomendado: Arkiv.** Corto, memorable, describe catálogo, no choca con Play/F-Droid/Obtainium/Aurora.

## Prerrequisitos

P08 y P09 (strings de UI ya reescritos se renombran una vez).

## Archivos afectados

- `app/build.gradle.kts` (`applicationId` L17, `outputFileName` L93)
- `app/src/main/AndroidManifest.xml` (`android:label`, activities)
- `app/src/main/res/values/themes.xml`
- `core/ui/src/main/res/values/strings.xml` y translations
- `app/src/main/res/values/strings.xml`
- launcher `mipmap-*`, `drawable/ic_stat_nova.xml`
- `app/.../NovaStoreApp.kt`, `NovaStoreRoot.kt` (rename de clase **opcional**)
- `NetworkModule.kt` User-Agent `"NovaStore/1.0"`
- `GitHubClient.kt` UA `NovaStore/5.0`
- `WorkScheduler` unique names `nova_update_scan`
- `README.md`, `docs/*`
- `settings.gradle.kts` `rootProject.name`

## Tareas

### P10-T01 Nombre visible Arkiv

**Qué:** `android:label`, `R.string.app_name`, títulos About, README header. No tocar applicationId.

**Aceptación:** launcher muestra Arkiv. grep usuario-visible “NovaStore” en strings.xml reducido a 0 (código package puede seguir `com.novastore`).

**Verificación:** `rg '>NovaStore<' app core/ui/src/main/res`; README.

### P10-T02 Iconos y splash

**Qué:** Nuevo adaptive icon (foreground simple, un glifo archivo/caja, color acento tokens). Capa monochrome Android 13 (`ic_launcher_monochrome`). Splash Core SplashScreen con background token. `ic_stat_*` blanco silhouette.

**Aceptación:** adaptive + monochrome existen. `ic_launcher_background` no `#FF270141` púrpura viejo si el acento cambió.

**Verificación:** files `mipmap-anydpi-v26/ic_launcher.xml`.

### P10-T03 README y docs

**Qué:** Nombre Arkiv, applicationId documentado `com.novastore.fork`, minSdk 26, no affiliation. APK artifact name `Arkiv-vX.Y.Z.apk` en `applicationVariants` **si** se cambia outputFileName (P10-T01).

**Aceptación:** README coherente con gradle versionName.

**Verificación:** lectura.

### P10-T04 UA y unique works

**Qué:** User-Agent `Arkiv/<versionName>`. Work names: cambiar unique names **rompe** el periodic work (se crea otro). Prescrito: **mantener** `nova_update_scan` para no duplicar workers en upgrades; solo UA y canales user-visible.

**Aceptación:** UA nuevo; unique work ids viejos.

**Verificación:** rg User-Agent.

### P10-T05 applicationId — NO cambiar por defecto

**Qué:** Decisión irreversible. Si el git log / installs usan `com.novastore.fork`, **prohibido** cambiar applicationId (Room `nova-store.db` y DataStore se perderían; dos iconos). Documentar en INFORME_FINAL.

Solo cambiar package/applicationId si el ejecutor verifica que **nunca** se publicó (no tag, no issue de usuarios). Entonces: `applicationId` `app.arkiv.store`, namespace opcionalmente igual, FileProvider authorities, InstallStatusReceiver action `com.novastore.app.INSTALL_STATUS` → nuevo. Migración: export JSON fuentes (P06) + instrucciones uninstall. **No** auto-migrar DB entre applicationIds.

**Aceptación:** `app/build.gradle.kts` applicationId sigue `com.novastore.fork` salvo justificación escrita en PROGRESO Bloqueado/Decisión.

**Verificación:** `grep applicationId app/build.gradle.kts`

**Riesgo:** datos. Reversión: no commitear el cambio.

### P10-T06 Rename clases NovaStore*

**Qué:** `NovaStoreApp` → `ArkivApp`, `NovaStoreRoot` → `ArkivRoot`, `NovaTheme` puede quedar (token). Manifest `android:name=".ArkivApp"`. Hilt `@HiltAndroidApp`. **No** rename de paquetes `com.novastore.app` (coste masivo, mismo applicationId).

**Aceptación:** app arranca. Tests compile.

**Verificación:** assembleRelease.

## Definición de hecho

UI dice Arkiv. Iconos nuevos. applicationId intacto salvo caso virgen documentado. UA actualizado. Workers unique names estables.

## Notas de decisión

Descartado “NovaStore 2”. Descartado cambiar namespace en el mismo ciclo que R8+providers (demasiado diff). Aurora/Obtainium/Fossify evitados.
