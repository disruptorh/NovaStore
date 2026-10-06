# P07 — Sistema de diseño y tema

## Objetivo

Tokens centralizados. Visual minimalista, una función máxima. 1 acento + neutros + semánticos. Claro/oscuro/AMOLED/dynamic.

## Prerrequisitos

P02 (no luchar con hex duplicados y luego borrar archivos). Independiente de P04–P06.

## Archivos afectados

- `core/ui/src/main/java/com/novastore/app/core/ui/theme/Color.kt`
- `core/ui/src/main/java/com/novastore/app/core/ui/theme/Theme.kt`
- `core/ui/src/main/java/com/novastore/app/core/ui/theme/Typography.kt`
- crear `Spacing.kt`, `Shape.kt`, `Motion.kt`, `Elevation.kt`
- `core/model/.../UiPrefs.kt` (`AccentPalette` 16 valores)
- `core/ui/components/*`
- `app/src/main/res/values/themes.xml`, `values-night/themes.xml`
- `feature/*/...` se migran en P08; aquí solo `core/ui` + theme XML

## Tokens prescritos

Espacio: 4, 8, 12, 16, 24, 32, 48 (`NovaSpacing`).

Radio: 8 (chip), 12 (card), 28 (sheet) (`NovaShapes`).

Elevación: 0 cards (outline), 2 search bar, 6 sheet.

Acento default: un solo `AccentPalette.NOVA` o reducir enum a: Default, Dynamic, + 4 acentos máximo (Ocean, Violet, Amber, Graphite). **Borrar** el resto de paletas y UI picker masivo.

Semánticos: `error`, `ok` (update disponible), `warning` (firma), `offline`. No usar acento para error.

Tipografía: scale M3; **prohibido** `fontSize = n.sp` fuera de Typography (arreglar 8.sp/10.sp en P08, definir `labelTiny` ≥ 11.sp para a11y).

## Tareas

### P07-T01 Spacing Elevation Radius

**Qué:** Crear objetos. Reemplazar en `core/ui/components` literales más repetidos.

**Aceptación:** `AppCards.kt`, `ScreenStates.kt`, `AppIcon.kt` no introducen dp sueltos nuevos; los existentes en esos 3 archivos migrados.

**Verificación:** `rg '[0-9]+\\.dp' core/ui/src/main/java/com/novastore/app/core/ui/components/AppCards.kt` → 0 o solo `NovaSpacing` refs.

### P07-T02 Paleta reducida

**Qué:** `Color.kt` deja de exportar 42 colores ad hoc. Scheme desde acento + neutros Paper/Ink. Quitar `Teal` muerto.

**Aceptación:** Theme compile; selector Settings de acentos ≤ 5 opciones + Dynamic.

**Verificación:** enum `AccentPalette` entries count ≤ 6.

### P07-T03 NovaShapes + Typography

**Qué:** `MaterialTheme(shapes = NovaShapes, typography = NovaTypography)`. Una `RoundedCornerShape` en features se reemplaza en P08; en core/ui ya no hay 14 radios.

**Aceptación:** `rg RoundedCornerShape core/ui` solo `Shape.kt`.

**Verificación:** rg.

### P07-T04 Temas

**Qué:** Light / Dark / AMOLED (surface `#000000`) / Follow system. DynamicColor API 31 si `ThemeMode.DYNAMIC` o toggle existente. XML `themes.xml`: `windowBackground` usa `@android:color/transparent` o color token; no blanco hardcode si AMOLED. Splash: `Theme.SplashScreen` (dependency `androidx.core:core-splashscreen`) o Compose first frame; no pantalla en blanco 1s.

**Aceptación:** tres modos visibles; AMOLED pixels negros en settings preview.

**Verificación:** compile; screenshot no obligatorio aquí (P08/P12).

### P07-T05 Componentes

**Qué:** APIs estables:

- `AppCard` (reemplaza Large/Row/Grid duplicados: un composable con `enum Density`)
- `NovaButton` (ya hay GradientButton: **simplificar** a Filled/Tonal/Text, sin gradiente hero salvo home header)
- `NovaChip`
- `NovaSearchBar`
- `NovaDialog` / `NovaSheet`
- `NovaEmpty`, `NovaError`, `NovaSkeleton` (ShimmerBox)

Migrar usos **dentro de core/ui**. Features en P08.

**Aceptación:** `SeeAllLink`/`AppCardGrid` o se usan o se borran (resolver el falso positivo de dead code).

**Verificación:** compile ui; 0 composable dead (rg nombre).

### P07-T06 Motion

**Qué:** `NovaMotion.short = 120ms`, `medium=200`, `emphasized` cubic. Transiciones Nav: fade+slight slide 8dp. Respetar `LocalAccessibilityManager.isReduceMotionEnabled` / `MotionDurationScale`.

**Aceptación:** constantes usadas en P08-T08; archivo Motion.kt existe.

**Verificación:** archivo + compile.

### P07-T07 Contraste

**Qué:** Comentario o test: onSurface vs surface ≥ 4.5:1 en light y dark. Acento no como texto sobre acento saturado. Documentar en Color.kt.

**Aceptación:** pares primary/onPrimary revisados.

**Verificación:** inspección; opcional test luminance.

### P07-T08 Migrar core/ui dp/hex

**Qué:** resto de components (`NovaGradients.kt`: o se usan tokens o se elimina hero brush si P08 lo descarta — prescrito: **un** `novaHeroBrush` desde acento, borrar brushes duplicados).

**Aceptación:** `rg 'Color\\(0x' core/ui/components` = 0.

**Verificación:** rg.

## Definición de hecho

Tokens existen y core/ui los usa. Paleta recortada. Shapes en theme. Componentes nombrados listos para P08.

## Notas de decisión

Descartado paleta de 16 acentos (ruido). Descarta glassmorphism y gradientes multi-stop en cards.
