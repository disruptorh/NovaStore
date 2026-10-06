# P01 — Línea base y métricas

## Objetivo

Congelar números medibles **antes** de tocar rendimiento o R8. Sin baseline no hay prueba de P02/P03/P12.

## Prerrequisitos

Ninguno. Primera ejecución.

## Archivos afectados

Crear (no existen hoy):

- `scripts/measure-apk.sh`
- `planes de implementacion/METRICAS_BASE.md` (el ejecutor lo crea; no es código de app)

Leer:

- `app/build.gradle.kts` (minify, output name)
- `app/build/outputs/apk/release/NovaStore-v7.2.3.apk` (si existe; baseline observado 18 MB el 2026-10-03)
- `.github/workflows/build.yml`

## Tareas

### P01-T01 Crear METRICAS_BASE.md y registrar tamaño APK

**Qué:** Crear `planes de implementacion/METRICAS_BASE.md` con tabla:

| Métrica | Objetivo | Baseline | Método |
|---|---|---|---|
| APK release universal | ≤ 12 MB | (medir) | `stat` del APK |
| APK por ABI (tras P02-T09) | ≤ 8 MB c/u | n/a | splits |
| Cold start time to first frame (release, dispositivo API ≥ 29) | ≤ 1.5 s | (medir) | Macrobenchmark o `adb shell am start -W` |
| Time to home contenido (repos cacheados, 2º arranque) | ≤ 800 ms | (medir) | mismo |
| RSS tras home idle 10 s | ≤ 250 MB | (medir) | `dumpsys meminfo` |
| Jank home scroll 5 s | ≤ 5% frames >16.6 ms | (medir) | `dumpsys gfxinfo` o Perfetto |
| `assembleRelease` | éxito | (medir) | Gradle |

**Pasos:** crear archivo; ejecutar `ls -lh app/build/outputs/apk/release/*.apk` o `./gradlew :app:assembleRelease` si no hay APK.

**Aceptación:** el archivo existe y la fila APK tiene número real en bytes.

**Verificación:** `test -f "planes de implementacion/METRICAS_BASE.md" && grep -E '[0-9]+' "planes de implementacion/METRICAS_BASE.md"`

**Riesgo:** rebuild lento. Reversión: no aplica (solo docs/scripts).

### P01-T02 Script measure-apk.sh

**Qué:** `scripts/measure-apk.sh` imprime path, bytes, `apkanalyzer apk file-size` si SDK presente, y lista dex/res si `apkanalyzer` existe.

**Pasos:** crear script executable; documentar uso en METRICAS_BASE.

**Aceptación:** `bash scripts/measure-apk.sh` exit 0 con bytes.

**Verificación:** ejecutar el script contra el APK release.

**Riesgo:** `apkanalyzer` ausente — el script debe caer a `stat`.

### P01-T03 Procedimiento arranque en frío

**Qué:** En METRICAS_BASE, sección “Cold start”: comandos exactos:

```
adb shell am force-stop com.novastore.fork
adb shell am start-activity -W -n com.novastore.fork/com.novastore.app.MainActivity
```

Registrar `TotalTime` y `WaitTime`. 3 repeticiones, mediana.

**Aceptación:** 3 números + mediana en el md.

**Verificación:** grep `TotalTime` o `mediana` en METRICAS_BASE. Si no hay dispositivo, marcar `NO_DEVICE` y usar Macrobenchmark stub en `app` **solo si** hay emulador; si no hay, documentar `UNVERIFIED` y **no** bloquear P02 (P12 reintenta).

**Riesgo:** sin device. Plan de reversión: n/a. No detener el loop entero: sigue P02.

### P01-T04 Jank

**Qué:** Procedimiento:

```
adb shell dumpsys gfxinfo com.novastore.fork reset
# scroll home 5s
adb shell dumpsys gfxinfo com.novastore.fork
```

Anotar janky frames / total.

**Aceptación:** método escrito; número si hay device.

**Verificación:** sección Jank en METRICAS_BASE.

### P01-T05 RAM

**Qué:** `adb shell dumpsys meminfo com.novastore.fork` tras idle home. Anotar TOTAL PSS.

**Aceptación:** método + valor o NO_DEVICE.

**Verificación:** sección RAM en METRICAS_BASE.

### P01-T06 Congelar baseline

**Qué:** Fecha, git SHA (`git rev-parse --short HEAD`), minify=false, APK 1 archivo. Commit `docs: P01 baseline metrics`.

**Aceptación:** SHA y fecha en METRICAS_BASE; P01 checklist hecha.

**Verificación:** `git log -1 --oneline` contiene P01.

## Definición de hecho

P01-T01…T06 hechas. Hay números o `NO_DEVICE` explícito. Objetivos de la tabla no se rebajan.

## Notas de decisión

Descartado exigir Macrobenchmark library ahora: 0 infra de benchmark en el repo; añadirla en P03/P12 si hay tiempo. `am start -W` basta para comparar antes/después.
