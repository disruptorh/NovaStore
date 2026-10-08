# METRICAS_BASE — línea base NovaStore

Congelado antes de tocar rendimiento, R8 o peso. Comparar siempre contra esta tabla.

## Contexto de la medición

| Campo | Valor |
|---|---|
| Fecha | 2026-10-06 |
| Git SHA | `53163e3` (HEAD de `main`, v7.2.3 build 14) |
| `isMinifyEnabled` | `false` |
| `isShrinkResources` | `false` |
| APK | 1 archivo universal `NovaStore-v7.2.3.apk` (incluye arm64-v8a, armeabi-v7a, x86, x86_64) |
| Dispositivo de medición | Samsung arm64-v8a, Android 14 (API 34), 1080×2400 @450 dpi |
| App instalada | `com.novastore.fork` versionCode 14 / versionName 7.2.3 (instalada 2026-10-03) |

## Tabla de métricas

| Métrica | Objetivo | Baseline | Método |
|---|---|---|---|
| APK release universal | ≤ 12 MB | **18 581 405 bytes (17.72 MB)** | `bash scripts/measure-apk.sh` |
| APK descarga estimada | — | 16 663 246 bytes | `apkanalyzer apk download-size` |
| APK por ABI (tras P02-T09) | ≤ 8 MB c/u | n/a (sin splits) | splits |
| Cold start time to first frame (release) | ≤ 1.5 s | **376 ms** (mediana de 504/376/329) | `adb shell am start -W` |
| Time to home contenido (2º arranque, repos cacheados) | ≤ 800 ms | **3 458 ms** (mediana de 3189/3726; proxy por estabilidad de captura) | captura cada 250 ms hasta 2 frames iguales |
| RSS tras home idle 10 s | ≤ 250 MB | **PSS 212 500 KB / RSS 270 380 KB** | `adb shell dumpsys meminfo` |
| Jank home scroll 5 s | ≤ 5% frames >16.6 ms | **6.64%** (20/301; legacy 14.95%) | `adb shell dumpsys gfxinfo` |
| `assembleRelease` | éxito | **BUILD SUCCESSFUL** (826 tareas, 17 s incremental) | `./gradlew :app:assembleRelease` |

> El "time to home contenido" usa un proxy de estabilidad de pantalla porque no hay
> Macrobenchmark en el repo. Sirve para comparar antes/después, no como cifra absoluta.
> Objetivo ≤ 800 ms **no se puede cumplir con este proxy** (incluye carga de imágenes de
> Coil y de red); se re-evalúa en P12 con el mismo método y se compara contra 3 458 ms.

### Ruido de build (suelo de medición)

Reconstruir **el mismo SHA** (`53163e3`) produce `18 597 789` bytes frente a los
`18 581 405` del artefacto del 2026-10-03: **±16 384 bytes (0.09%)** de variación por
marcas de tiempo y orden de entradas ZIP. Cualquier delta de P12 menor que 0.1% no es
una mejora real.

## P01-T02 — Script de medición de APK

```bash
bash scripts/measure-apk.sh            # APK más reciente en app/build/outputs/apk/release/
bash scripts/measure-apk.sh ruta.apk   # APK concreto
```

Imprime path, bytes, `apkanalyzer apk file-size` / `download-size` (si el SDK está en
`$ANDROID_HOME` o `~/Android/Sdk`), librerías nativas por ABI, 20 entradas más grandes
(dex/res) y totales comprimidos por directorio. Si `apkanalyzer` no existe, cae a `stat`.

## P01-T03 — Arranque en frío

```bash
adb shell am force-stop com.novastore.fork
sleep 3
adb shell am start -W -n com.novastore.fork/com.novastore.app.MainActivity
```

Registrar `TotalTime` y `WaitTime` de cada tanda. 3 repeticiones, mediana.

### Resultado baseline (3 tandas)

| # | TotalTime (ms) | WaitTime (ms) | LaunchState |
|---|---|---|---|
| 1 | 504 | 510 | COLD |
| 2 | 376 | 385 | COLD |
| 3 | 329 | 333 | COLD |

**Mediana TotalTime: 376 ms.**

## P01-T04 — Jank (frames)

```bash
adb shell am force-stop com.novastore.fork
adb shell am start -W -n com.novastore.fork/com.novastore.app.MainActivity
sleep 6
adb shell dumpsys gfxinfo com.novastore.fork reset
# 5 swipes de scroll en Home (~5 s)
adb shell input swipe 540 1900 540 700 700   # ×5
adb shell dumpsys gfxinfo com.novastore.fork
```

Anotar `Janky frames / Total frames rendered`.

### Resultado baseline

```
Total frames rendered: 301
Janky frames: 20 (6.64%)
Janky frames (legacy): 45 (14.95%)
50th percentile: 12ms   90th: 20ms   95th: 31ms   99th: 57ms
Number Missed Vsync: 6   Number High input latency: 303   Number Slow UI thread: 20
```

**Baseline jank: 6.64%** (objetivo ≤ 5%).

## P01-T05 — RAM

```bash
# dejar Home idle 10 s
adb shell dumpsys meminfo com.novastore.fork
```

Anotar `TOTAL PSS` y `TOTAL RSS` del bloque *App Summary*.

### Resultado baseline

| Campo | KB |
|---|---|
| Java Heap | 32 856 |
| Native Heap | 87 052 |
| Code | 23 896 |
| Graphics | 56 784 |
| **TOTAL PSS** | **212 500** |
| **TOTAL RSS** | **270 380** |

## P01-T06 — Congelar baseline

- Fecha: 2026-10-06
- Git SHA: `53163e3`
- Minify: `false` · Shrink resources: `false`
- APK: 1 archivo universal de 18 581 405 bytes
- Dispositivo: Android 14 / API 34 / arm64-v8a
- Commit: `docs: P01 baseline metrics`

Reintentos de dispositivo en P12 (métodos idénticos, misma rama de dispositivo).

## P12-T01 — Re-medición (after) · 2026-10-08

| Métrica | Baseline | After P12 | Delta |
|---|---|---|---|
| APK release universal | 18 581 405 bytes | **6 337 308 bytes (6.04 MB)** | **−65.9 %** |
| APK descarga estimada (universal) | 16 663 246 | **4 703 520** | −71.8 % |
| APK arm64-v8a | n/a (sin splits) | **5 227 021** (descarga 4 296 156) | splits |
| APK armeabi-v7a | n/a | **5 078 103** (descarga 4 271 338) | splits |
| APK x86_64 | n/a | **5 222 428** (descarga 4 296 104) | splits |
| Cold start → first frame (release, mediana 3) | 376 ms | **392 ms** (420/352/392) | +16 ms |
| Time to home contenido (2ª arranque, warm) | 3 458 ms | **3 190 ms** (3 940/2 440) | −268 ms |
| RSS/PSS tras home idle 10 s | RSS 270 380 / PSS 212 500 KB | **RSS 195 220 / PSS 133 120 KB** | −27.8 % / −37.4 % |
| Jank home scroll 5 s | 6.64 % (20/301) | **5.99 %** (17/284) | −0.65 p.p. |
| `assembleRelease` | éxito | **BUILD SUCCESSFUL** | = |

### Contexto after

| Campo | Valor |
|---|---|
| Fecha | 2026-10-08 |
| Git SHA | `44ed949` (v7.3.0 build 15, minify+shrink on, splits ABI) |
| APKs | universal `NovaStore-v7.3.0.apk` (6 337 308 B) + arm64-v8a / armeabi-v7a / x86_64 (≤ 5.23 MB c/u) |
| Dispositivo | Samsung arm64-v8a, Android 14 (API 34), 1080×2400 @450 dpi (misma rama; APK re-instalado con `adb install -r`) |

> Ruido de build: reconstruir el mismo SHA da ±16 KB (0.09 %); todos los deltas de
> tamaño están órdenes de magnitud por encima del ruido. Los tiempos de arranque y
> contenido usan el mismo proxy de captura que P01 (no Macrobenchmark); el jank
> sigue por encima del objetivo ≤ 5 % (5.99 %) pero mejora sobre el 6.64 % de la
> baseline y **no es un criterio de cierre del 99**.
