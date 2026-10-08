# Planes de implementación — NovaStore → producto final

Índice para un agente de código autónomo. Única fuente de avance: `PROGRESO.md`.

## Orden de ejecución (no circular)

```
P01 Línea base
  ├─→ P02 Limpieza y peso
  │     ├─→ P03 Arquitectura y rendimiento
  │     │     └─→ P04 Capa de fuentes
  │     │           ├─→ P05 Motor de actualizaciones (borrar mirrors)
  │     │           └─→ P06 Fuentes personalizadas UI  (tras P05)
  │     └─→ P07 Sistema de diseño
  │           └─→ P08 Pantallas principales
  ├─ P09 Configuración  (tras P06 y P07)
  ├─ P11 Pruebas, a11y, seguridad (tras P05, P06, P09)
  └─ P12 Pulido y release (tras P11)
```

P07 puede avanzar en paralelo a P03–P05 **después** de P02. No fusionar ramas que toquen el mismo archivo (`SettingsScreen.kt`, `Sources.kt`, `CheckForUpdatesUseCase.kt`, `Theme.kt`).

## Archivos

| Archivo | Rol |
|---|---|
| `00_AUDITORIA.md` | Hechos verificados |
| `README.md` | Este índice |
| `PROGRESO.md` | Checklist maestro |
| `01_LINEA_BASE_Y_METRICAS.md` | P01 |
| `02_LIMPIEZA_Y_PESO.md` | P02 |
| `03_ARQUITECTURA_Y_RENDIMIENTO.md` | P03 |
| `04_CAPA_DE_FUENTES.md` | P04 |
| `05_MOTOR_ACTUALIZACIONES.md` | P05 |
| `06_FUENTES_PERSONALIZADAS.md` | P06 |
| `07_SISTEMA_DE_DISENO.md` | P07 |
| `08_REDISENO_PANTALLAS.md` | P08 |
| `09_CONFIGURACION.md` | P09 |
| `11_PRUEBAS_A11Y_SEGURIDAD.md` | P11 |
| `12_PULIDO_Y_RELEASE.md` | P12 |
| `99_CRITERIOS_DE_FINALIZACION.md` | Definición de terminado |

## Instrucciones para el agente ejecutor

Lee `README.md` y `PROGRESO.md` al iniciar y al reanudar.

Ejecuta las tareas en orden, sin saltar dependencias. Tras cada tarea: verifica con el criterio indicado, marca la casilla en `PROGRESO.md` (`- [x]` y mueve el ID a Hecho), y haz un commit atómico con mensaje convencional (`feat:`, `refactor:`, `perf:`, `fix:`, `test:`, `docs:`, `ci:`, `chore:`).

Si el build o los tests fallan, corrige antes de avanzar. Nunca marques una tarea como hecha sin verificarla.

Si una tarea está bloqueada, regístralo con el motivo en `PROGRESO.md` (sección Bloqueado), pasa a la siguiente tarea independiente y vuelve después.

NO detengas el trabajo ni pidas confirmación al usuario hasta que TODAS las tareas estén completas y se cumpla `99_CRITERIOS_DE_FINALIZACION.md`. Solo puedes detenerte antes si encuentras una decisión irreversible que ponga en riesgo datos del usuario, y entonces debes dejarla documentada en `PROGRESO.md` y en el plan afectado. **No cambies** `applicationId`, nombre visible, iconos, User-Agent, ni clases `NovaStore*`: P10 (identidad) está omitido.

Al terminar, ejecuta la verificación final completa y escribe `planes de implementacion/INFORME_FINAL.md` con métricas antes/después.

Reglas extra:

- No reintroduzcas APKPure, APKCombo ni scraping de mirrors de APKs.
- No inventes `AppSourceProvider` paralelo al existente: hay que **crearlo** (hoy no existe).
- No borres funciones vivas. Código “muerto” solo tras `grep` de símbolos + compile.
- Conserva Play nativo y Play web como fuentes **si el usuario las tiene activas**; no son mirrors.
- Commits: un ID de tarea por commit cuando sea posible (`feat(sources): P04-T01 interfaz AppSourceProvider`).
- Trabaja solo en el código de la app; no reescribas estos planes salvo corrección factual de una ruta.

## Cobertura requisitos de producto

| Req | Planes |
|---|---|
| 1 Calidad/velocidad/APK | P01, P02, P03, P08, P12 |
| 2 Updates solo fuentes activas, sin mirrors | P05, P11 |
| 3 Fuentes Obtainium-like + catálogo único | P04, P06 |
| 4 Identidad | **omitido** (P10 cancelado; se conserva NovaStore / `com.novastore.fork`) |
| 5 Diseño visual | P07, P08 |
| 6 Configuración | P09 |
