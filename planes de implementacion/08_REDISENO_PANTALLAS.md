# P08 — Rediseño de pantallas principales

## Objetivo

Cada pantalla usa tokens P07, estados carga/vacío/error/offline, listas virtualizadas, 48 dp táctil, sin hex/dp sueltos nuevos.

## Prerrequisitos

P07-T01…T05. P03-T04…T07 (paging/keys). P05 para Updates sin mirrors.

## Archivos afectados

- `feature/home/HomeScreen.kt` (1270 L — partir si >400 L por archivo)
- `feature/home/HomeViewModel.kt`, `PlayShelves.kt`, `CategoryScreen.kt`
- `feature/search/SearchScreen.kt`, `SearchViewModel.kt`
- `feature/details/AppDetailsScreen.kt`, `DetailsExtraSections.kt`, `AppDetailsViewModel.kt`
- `feature/downloads/DownloadsScreen.kt`
- `feature/updates/UpdatesScreen.kt`, `IgnoredScreen.kt`
- `feature/installed/InstalledScreen.kt`
- `feature/account/AccountScreen.kt`
- `app/.../NovaStoreRoot.kt` (fontSize 10.sp L329)
- `core/ui/components/ScreenStates.kt`

Regla: extraer secciones a archivos nuevos **en el mismo feature** si el archivo sigue >600 L. No cambiar comportamiento de download/install.

## Tareas

### P08-T01 Home / descubrir

**Qué:** Layout: search bar tokens, tiles (updates/instaladas/descargas), shelves ≤20, shimmer skeleton no spinner. OfflineBanner y ErrorState con retry. Quitar gradientes inline `HomeScreen.kt` ~536. Usar `NovaSearchBar`, `AppCard`.

**Aceptación:** 0 `Color(0x` en HomeScreen; Loading usa Skeleton; empty catalog = EmptyState CTA “Añadir fuente”.

**Verificación:** compile feature home; rg hex.

### P08-T02 Búsqueda

**Qué:** Debounce ya si existe; skeleton en primer load. Empty query vs empty results distintos. Quitar `8.sp` (`SearchScreen.kt:406`) → Typography. Grid/lista con keys. Offline: searchLocal.

**Aceptación:** fontSize.sp raw = 0 en search. Estados cubiertos.

**Verificación:** rg `\\.sp` SearchScreen.

### P08-T03 Detalle

**Qué:** Header compacto (icon, name, source chip, acción primaria una). Screenshots LazyRow. Versions list. Preferred source chips (P06-T06 si hecho; si no, placeholder API). Touch star 48 dp (hoy `DetailsExtraSections.kt:224` 20.dp).

**Aceptación:** botón instalar ≥48 dp; ErrorState si red falla; skeleton header.

**Verificación:** rg `size = 20.dp` details.

### P08-T04 Descargas

**Qué:** Lista virtual. Estados: vacío, error, progreso. Acciones pausa/cancel visibles. Auto-clean copy en empty.

**Aceptación:** EmptyState si cola vacía.

**Verificación:** compile.

### P08-T05 Actualizaciones

**Qué:** Agrupar por fuente **activa**. Sin labels APKPure. Banner scan. Update all. Ignoradas vía ruta existente. Target 42.dp (`UpdatesScreen.kt:599`) → 48.

**Aceptación:** 0 strings apkpure/apkcombo. 48 dp.

**Verificación:** rg apkpure UpdatesScreen.

### P08-T06 Biblioteca / instaladas

**Qué:** Lista virtual, search local, uninstall confirm (hoy puede faltar). Empty. Iconos via PackageManager (P03-T08).

**Aceptación:** confirm uninstall; 48 dp rows.

**Verificación:** Dialog presente.

### P08-T07 Cuenta

**Qué:** Misma densidad/tokens. No rediseñar OAuth (comportamiento). Banners Info unificados (`InfoBanner` único en core/ui).

**Aceptación:** 0 banner duplicado copy-paste; usa componente P07.

**Verificación:** compile account.

### P08-T08 Nav + transiciones

**Qué:** `NovaStoreRoot`: NavigationBar tokens, badge updates, `fontSize` 10.sp → style. Nav transitions `NovaMotion`. Rutas intactas (no romper deep links `MainActivity`).

**Aceptación:** 0 `.sp` raw en Root. Deep links compile.

**Verificación:** rg `10.sp`.

### P08-T09 Estados globales

**Qué:** Checklist por pantalla: Loading skeleton, Empty, Error+retry, Offline. Home, Search, Details, Downloads, Updates, Installed, Category, Settings (P09), Account.

**Aceptación:** tabla en INFORME o comentario PR; cada pantalla tiene los 4 o N/A justificado (Account sin empty catalog).

**Verificación:** inspección ScreenStates imports en cada Screen.kt.

### P08-T10 A11y pantallas

**Qué:** contentDescription en icon-only. AutoMirrored en chevrons. Clickable min 48. Semantics en tabs.

**Aceptación:** los 6 casos `uiux_audit` E tocados.

**Verificación:** rg `size = 32.dp` Settings se arregla en P09; aquí details+updates+search.

## Definición de hecho

Siete superficies visuales (home, search, details, downloads, updates, installed, nav) usan tokens. Estados presentes. Sin mirrors en UI updates.

## Notas de decisión

No bottom bar de 5 tabs. Search permanece ruta (no tab) para no apiñar. Partir archivos >600 L es parte de T01/T03, no “refactor opcional”.
