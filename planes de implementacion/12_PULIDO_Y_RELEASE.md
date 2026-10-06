# P12 — Pulido final, verificación y release

## Objetivo

Cerrar el loop: métricas vs P01, lint, APK firmable, INFORME_FINAL, criterios 99.

## Prerrequisitos

P11 hecho. Todas las casillas previas `[x]` o bloqueadas con motivo que no impida release (solo P01 device=NO_DEVICE permitido).

## Archivos afectados

- `planes de implementacion/METRICAS_BASE.md` (columna “después”)
- `planes de implementacion/INFORME_FINAL.md` (crear)
- `PROGRESO.md`
- `app/build.gradle.kts` versionName bump **solo si** el ejecutor completa 99: `7.3.0` / versionCode 15
- lint outputs

## Tareas

### P12-T01 Re-medir

**Qué:** Repetir P01-T02…T05. Completar tabla after. APK debe ser < baseline. Si ABI splits: listar tamaños.

**Aceptación:** números en METRICAS_BASE after ≥ filas que tenían baseline.

**Verificación:** archivo actualizado.

### P12-T02 assembleRelease + lint

**Qué:** `./gradlew :app:assembleRelease lintVitalRelease testReleaseUnitTest`. 0 errors. Warnings conocidos listados en INFORME (máx 20 no bloqueantes).

**Aceptación:** exit 0.

**Verificación:** comandos.

### P12-T03 Checklist 99

**Qué:** Recorrer `99_CRITERIOS_DE_FINALIZACION.md` ítem a ítem; marcar en INFORME.

**Aceptación:** todos sí o N/A justificado.

**Verificación:** lectura cruzada.

### P12-T04 INFORME_FINAL.md

**Qué:** Secciones: SHA git, métricas before/after, mirrors ausentes (`rg apkpure` resultado), applicationId, riesgos residuales (SHA opcional en Play, no pinning), cómo medir.

**Aceptación:** archivo existe en `planes de implementacion/INFORME_FINAL.md`.

**Verificación:** test -f.

## Definición de hecho

99 cumplido. INFORME escrito. PROGRESO todo `[x]` o bloqueos no-release documentados.

## Notas de decisión

No publicar GitHub Release (el agente no sube artefactos salvo que el usuario lo pida en otro hilo). Solo produce APK local.
