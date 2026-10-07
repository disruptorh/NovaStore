# PROGRESO — tablero maestro

Leyenda de estado: **Pendiente** (casilla vacía) · **En curso** (marca `~` y pon el ID aquí abajo) · **Hecho** (`[x]`) · **Bloqueado** (sección al final).

Actualiza este archivo en el mismo commit que cierra la tarea.

## En curso

(ninguno)

## Hecho

- P01-T01…P01-T06 (ver `01_LINEA_BASE_Y_METRICAS.md` y `METRICAS_BASE.md`)
- P02-T01…P02-T11 (R8+shrink on, splits ABI, WebP launcher; smoke en device real: home/settings/details F-Droid OK)
- P03-T01 (core:updater → data/updater; módulo eliminado)
- P03-T02 (índices Room + migración 4→5; schema JSON exportado; test MigrationTestHelper; upgrade device OK)
- P03-T03 (OkHttp cache 50 MiB en NetworkModule; test MockWebServer; smoke device)
- P03-T04 (límites de sección ≤20; filas lazy con key; feed paginado ya existente)
- P03-T05 (búsqueda local paginada a 40 con load-more; `search` DAO con OFFSET + test; merge de fuentes intacto)
- P03-T06 (infinite scroll de categorías ya presente: `CategoryViewModel` página con `listByCategory(offset, PAGE=90)` + `CategoryScreen` nearEnd→loadMore)
- P03-T07 (keys+contentType en todas las Lazy lists de features: Search, Home, Updates, Installed, Downloads, Ignored, Category, Details)
- P03-T08 (iconos instalados ya no se persisten: scan/upsert escriben `icon = null`; UI lee de PackageManager vía `InstalledAppIcon` con LruCache off-main)
- P03-T09 (auditoría de hilos OK: 0 `runBlocking` en features, `fetchIndex`/`parseIndex` en IO, `replaceSource`/`ensureBuiltIns` Room suspend, bloqueo de features en `Dispatchers.IO`)
- P03-T10 (baseline profile con path de cold start: `app/src/main/baseline-prof.txt` con MainActivity/NovaStoreApp/NovaStoreRoot; APK sigue empaquetando `assets/dexopt/baseline.prof`+`.profm`)

## Bloqueado

(ninguno)

---

## P01 Línea base y métricas

- [x] P01-T01 Crear `METRICAS_BASE.md` y registrar tamaño APK/AAB actual
- [x] P01-T02 Script `scripts/measure-apk.sh` (tamaño, método, ABI)
- [x] P01-T03 Procedimiento de arranque en frío (adb + Macrobenchmark o manual)
- [x] P01-T04 Procedimiento de jank/frames (Perfetto o dump gfxinfo)
- [x] P01-T05 Procedimiento de RAM (meminfo)
- [x] P01-T06 Congelar números baseline en `METRICAS_BASE.md`

## P02 Limpieza y peso

- [x] P02-T01 Eliminar `UninstallAppUseCase`
- [x] P02-T02 Eliminar `RefreshRepositoriesUseCase`
- [x] P02-T03 Eliminar `try_community_dispensers`
- [x] P02-T04 Quitar Retrofit si sigue sin usos Kotlin
- [x] P02-T05 Quitar `okhttp-logging` si el interceptor no se usa
- [x] P02-T06 Deduplicar docs raíz vs `docs/`
- [x] P02-T07 Corregir `run-tests.sh`
- [x] P02-T08 Activar R8 + `shrinkResources` en release
- [x] P02-T09 Splits ABI o `abiFilters`
- [x] P02-T10 WebP/compresión de PNGs de branding/launcher sin pérdida visual
- [x] P02-T11 ProGuard keep rules para Hilt/Work/kotlinx.serialization/Room

## P03 Arquitectura y rendimiento

- [x] P03-T01 Romper inversión `core:updater` → `domain`
- [x] P03-T02 Índices Room catálogo/versiones + migración 4→5
- [x] P03-T03 OkHttp `Cache` en `NetworkModule`
- [x] P03-T04 Paging Home (no cargar catálogo entero)
- [x] P03-T05 Paging Search
- [x] P03-T06 Infinite scroll categorías (`listByCategory` ya tiene OFFSET)
- [x] P03-T07 Keys estables + contentType en Lazy lists
- [x] P03-T08 Iconos instalados: dejar de meter PNG grandes en Room o comprimir
- [x] P03-T09 Dispatcher IO en refresh/parse de índices (auditoría de hilos)
- [x] P03-T10 Baseline Profile keep (ya hay `.dm` en APK; verificar `baseline-prof.txt`)

## P04 Capa de fuentes

- [x] P04-T01 Interfaz `AppSourceProvider` en domain
- [ ] P04-T02 Modelo `RepositoryConfig` + tipo de proveedor + priority
- [ ] P04-T03 `SourceRegistry` (solo fuentes enabled)
- [ ] P04-T04 Provider F-Droid/index (envolver cliente actual)
- [ ] P04-T05 Provider GitHub Releases
- [ ] P04-T06 Provider GitLab / Gitea / Codeberg
- [ ] P04-T07 Provider URL directa / HTML + regex
- [ ] P04-T08 Validación URL + preview de fetch
- [ ] P04-T09 Documentar alta de provider nuevo (reescribir `docs/source-providers.md` a la realidad)
- [ ] P04-T10 Tests unitarios de registry y URL

## P05 Motor de actualizaciones

- [ ] P05-T01 Borrar `ApkPureClient` y `ApkComboClient`
- [ ] P05-T02 Quitar `mirrorVersionsFor` y `mergeMirrorVersions`
- [ ] P05-T03 Reescribir `CheckForUpdatesUseCase` sin etapa MIRRORS
- [ ] P05-T04 Quitar keys/toggles APKPure/APKCombo + migración DataStore
- [ ] P05-T05 Quitar constantes/UI/tests de fuentes mirror (salvo parseo de links externos si se mantiene)
- [ ] P05-T06 Scan solo contra providers enabled + Play si toggle Play activo
- [ ] P05-T07 Unificar política de dedup (preferida → prioridad → versionCode)
- [ ] P05-T08 Separar `SOURCE_PLAY_WEB` de `SOURCE_PLAY`
- [ ] P05-T09 WorkManager: constraints, intervalo, `BootReceiver` re-arma scan
- [ ] P05-T10 Notificaciones INSTALLATION y ERRORS
- [ ] P05-T11 Tests `UpdateEngine` / `SourceResolutionPolicy` / scan sin mirrors

## P06 Fuentes personalizadas

- [ ] P06-T01 UI add/edit con tipo de proveedor
- [ ] P06-T02 Preview de resultados al añadir
- [ ] P06-T03 Enable/disable + delete con confirmación
- [ ] P06-T04 Reordenar prioridad persistente (`ensureBuiltIns` no pisa)
- [ ] P06-T05 Import/export JSON
- [ ] P06-T06 Fuente preferida por app en detalle
- [ ] P06-T07 Catálogo único: misma regla de dedup en DAO y updates
- [ ] P06-T08 Integridad hash/firma en flujo de alta (aviso si el índice no trae sha256)

## P07 Sistema de diseño

- [ ] P07-T01 Tokens Spacing (4/8) Elevation Radius
- [ ] P07-T02 Paleta: 1 acento + neutros + semánticos
- [ ] P07-T03 `NovaShapes` + tipografía
- [ ] P07-T04 Tema claro/oscuro/AMOLED/dynamic
- [ ] P07-T05 Componentes: AppCard, Button, Chip, SearchBar, Dialog, Sheet, Empty/Error/Skeleton
- [ ] P07-T06 Tokens de motion
- [ ] P07-T07 Contraste WCAG AA documentado en tokens
- [ ] P07-T08 Migrar `core/ui` off hex/dp crudos

## P08 Rediseño pantallas

- [ ] P08-T01 Home / descubrir
- [ ] P08-T02 Búsqueda
- [ ] P08-T03 Detalle
- [ ] P08-T04 Descargas
- [ ] P08-T05 Actualizaciones
- [ ] P08-T06 Biblioteca / instaladas
- [ ] P08-T07 Cuenta (consistencia visual)
- [ ] P08-T08 Nav root + transiciones
- [ ] P08-T09 Estados carga/vacío/error/offline en cada pantalla de P08
- [ ] P08-T10 Targets 48 dp y contentDescription en pantallas tocadas

## P09 Configuración

- [ ] P09-T01 Reagrupar secciones requeridas
- [ ] P09-T02 Búsqueda dentro de ajustes
- [ ] P09-T03 Descripciones y defaults
- [ ] P09-T04 Confirmación destructiva
- [ ] P09-T05 Privacidad / backup / acerca de
- [ ] P09-T06 Extraer SettingsScreen blob a archivos por sección

## P10 Identidad

- [ ] P10-T01 Aplicar nombre visible **Arkiv** (strings, labels)
- [ ] P10-T02 Iconos, splash, `ic_stat`
- [ ] P10-T03 README y docs de usuario
- [ ] P10-T04 User-Agent / canales / nombres WorkManager únicos
- [ ] P10-T05 **NO** cambiar `applicationId` si hay datos; documentar en INFORME. Solo si repo virgen: package rename
- [ ] P10-T06 Renombrar clases `NovaStore*` de UI (opcional, mismo commit) sin mover `applicationId`

## P11 Pruebas, a11y, seguridad

- [ ] P11-T01 SHA-256 mandatory o rechazo explícito `Unverified`
- [ ] P11-T02 Firma: no instalar si digest instalado falta y hay app instalada
- [ ] P11-T03 Rechazar `http://` en descargas; `network_security_config`
- [ ] P11-T04 Migrar tokens plaintext `SessionCipher`
- [ ] P11-T05 Tests RootCommand regex
- [ ] P11-T06 Tests providers + update scan
- [ ] P11-T07 CI: `testReleaseUnitTest` + script tests arreglado
- [ ] P11-T08 A11y RTL + TalkBack strings
- [ ] P11-T09 Revisar permisos storage

## P12 Pulido y release

- [ ] P12-T01 Re-medir métricas vs baseline
- [ ] P12-T02 `assembleRelease` + lint vital
- [ ] P12-T03 Checklist `99_CRITERIOS_DE_FINALIZACION.md`
- [ ] P12-T04 Escribir `INFORME_FINAL.md`
