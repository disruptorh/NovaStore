# P03 — Arquitectura y rendimiento

## Objetivo

Arranque y scroll de nivel tienda: índices, paging, caché HTTP, capa updater sin invertir dependencias, trabajo pesado en IO.

## Prerrequisitos

P02-T08 (medir sobre APK minify). P01 para comparar.

## Archivos afectados

- `core/updater/src/main/java/com/novastore/app/core/updater/UpdateEngine.kt` (imports domain L12-18)
- `core/updater/src/main/java/com/novastore/app/core/updater/di/UpdaterModule.kt`
- `core/updater/build.gradle.kts`
- `data/src/main/java/com/novastore/app/data/repository/UpdatesRepositoryImpl.kt` (import updater)
- `core/database/.../entity/Entities.kt`
- `core/database/.../migration/Migrations.kt`
- `core/database/.../NovaDatabase.kt` (version 4 → 5)
- `core/database/.../dao/CatalogDao.kt`
- `core/network/.../di/NetworkModule.kt`
- `feature/home/.../HomeViewModel.kt`, `HomeScreen.kt`
- `feature/search/.../SearchViewModel.kt`, `SearchScreen.kt`
- `feature/home/.../CategoryViewModel.kt`, `CategoryScreen.kt`
- `app/.../NovaStoreApp.kt` (Coil ya OK; no duplicar)
- `core/database/.../entity/Entities.kt` campo `icon` en `InstalledAppEntity`

## Tareas

### P03-T01 Inversión updater→domain

**Qué:** `UpdateEngine` no debe vivir en `core` dependiendo de repositorios domain. Mover `UpdateEngine` + `SourceResolutionPolicy` a `data/` (p.ej. `data/src/main/java/com/novastore/app/data/updater/`) o a `domain` **sin** que `core:updater` dependa de domain.

Opción prescrita: **mover a `data`**, dejar `core:updater` vacío o eliminarlo si solo esos 5 kt. `UpdateCheckService` se implementa en data. Quitar `implementation(project(":domain"))` de `core/updater`.

**Pasos:** mover archivos, ajustar gradle `data`, `app`, borrar módulo updater si 0 fuentes, quitar `include(":core:updater")` en `settings.gradle.kts` y deps en `app/build.gradle.kts:112`.

**Aceptación:** `./gradlew :core:updater:dependencies` no existe o no lista domain; graph `data` → domain → core (sin core→domain). Compile.

**Verificación:** `rg 'project\(":domain"\)' core/updater` vacío; `./gradlew :app:assembleRelease`

**Riesgo:** Hilt modules. Reversión: revert commit.

### P03-T02 Índices Room + migración 5

**Qué:** En `RemoteAppEntity` índices: `source`, `packageName`, `lastUpdatedAt`. En `AppVersionEntity`: `packageName`, `source`. `UpdateEntity.state`. `RepositoryEntity(enabled, priority)`. Subir `NovaDatabase` a version 5. `MIGRATION_4_5` con `CREATE INDEX`.

**Aceptación:** app arranca sobre DB v4 existente (no destruye). `exportSchema` actualizado.

**Verificación:** test instrumentado o unit Room MigrationTestHelper si se añade; como mínimo `assembleRelease` + inspección SQL en schema JSON `core/database/schemas/`.

**Riesgo:** migración mala borra catálogo. Reversión: version 4 + destructive no permitido. Probar upgrade.

### P03-T03 OkHttp Cache

**Qué:** En `NetworkModule.provideOkHttpClient`, `.cache(Cache(File(context.cacheDir, "http_cache"), 50L * 1024 * 1024))`. Inyectar `@ApplicationContext`. Índices F-Droid ya usan ETag; el cache cubre GitHub/GitLab/Play web.

**Aceptación:** directorio `http_cache` aparece tras search; 304 siguen funcionando en `FdroidIndexClient`.

**Verificación:** compile; test opcional MockWebServer.

### P03-T04 Paging Home

**Qué:** Home no materializa miles de `RemoteApp` de una vez. Shelves: límite duro (p.ej. 20 por shelf) ya en query. “See all” usa `CategoryScreen` paginado. `observeRecentlyAdded(limit)` ya tiene limit — fijar ≤ 30.

**Pasos:** leer `HomeViewModel.kt`; cortar listas; `LazyColumn` items con `key = app.packageName`.

**Aceptación:** dump de `HomeUiState` no contiene >N apps por sección (N documentado en código, default 20). Scroll 60 fps target (re-medir P12).

**Verificación:** code review + gfxinfo si device.

### P03-T05 Paging Search

**Qué:** `CatalogDao.search` LIMIT 200. Añadir OFFSET o PagingSource. UI: cargar más al final de `LazyColumn`/`LazyVerticalGrid` en `SearchScreen.kt`. Play/GitHub/GitLab: no fusionar 4 fuentes completas en main thread; `withContext(IO)` ya en repo — verificar ViewModel no hace `.toList()` pesado en Main.

**Aceptación:** scroll de resultados no clona listas innecesarias; query local pagina de 40.

**Verificación:** logs de tamaño de lista; unit test DAO offset.

### P03-T06 Infinite scroll categorías

**Qué:** `CategoryViewModel` debe usar `listByCategory(offset, limit)` (`CatalogDao.kt:113`). Si hoy carga una sola página, encadenar al `onScroll` último ítem.

**Aceptación:** categoría F-Droid “Internet” muestra >1 página sin OOM.

**Verificación:** test ViewModel con fake DAO o prueba manual.

### P03-T07 Keys Lazy

**Qué:** Todas las `LazyColumn`/`Grid` de features: `key` y `contentType`. Archivos: `HomeScreen.kt`, `SearchScreen.kt`, `UpdatesScreen.kt`, `InstalledScreen.kt`, `DownloadsScreen.kt`, `IgnoredScreen.kt`, `CategoryScreen.kt`.

**Aceptación:** no `item { }` sin key en listas de apps.

**Verificación:** `rg "items\\(" feature --glob '*Screen.kt'` y revisar.

### P03-T08 Iconos instalados

**Qué:** `InstalledAppEntity.icon: ByteArray?` infla DB. Guardar solo si size ≤ 32 KiB o dejar de persistir y leer de `PackageManager` (ya hay `InstalledAppIcon.kt` con cache en memoria). Preferir **no persistir bytes**; migración 5 pone columna ignorada o nulls.

**Aceptación:** scan de instaladas no escribe blobs > umbral; DB size baja en dispositivo de prueba.

**Verificación:** query DB size; code path `InstalledAppDao`.

### P03-T09 Hilos

**Qué:** Grep `runBlocking` en features y `Dispatchers.Main` para parse/network. `FdroidIndexClient.fetchIndex` ya `withContext(io)` (L65). Asegurar `replaceSource` no se llama desde Main (repo debe IO). `ensureBuiltIns` no en UI thread sin withContext.

**Aceptación:** 0 `runBlocking` en `feature/*`. Refresh no StrictMode disk en Main (si StrictMode se activa en debug).

**Verificación:** `rg runBlocking feature`; compile.

### P03-T10 Baseline profile

**Qué:** El APK ya empaqueta `.dm`. Verificar `app/src/main/baseline-prof.txt` o generate. Si falta fuente, copiar de `app/build/intermediates/.../baseline-prof.txt` a `app/src/main`.

**Aceptación:** release incluye profile; cold start path `MainActivity`/`NovaStoreRoot` cubierto.

**Verificación:** unzip APK lista `baseline.prof` / `.dm`.

## Definición de hecho

Módulo updater no depende de domain. DB v5 con índices. HTTP cache. Listas con keys y paging en home/search/categoría. Sin runBlocking en features.

## Notas de decisión

Paging 3 library no es obligatoria si OFFSET+scroll es menos código; usar Paging 3 **solo** si el ViewModel se vuelve un spagueti de offsets. Descartado Room `autoMigrations` sin test por el riesgo de wipe.
