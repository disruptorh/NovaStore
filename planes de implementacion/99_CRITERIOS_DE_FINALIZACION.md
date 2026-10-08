# 99 — Criterios de finalización

El trabajo está **terminado** solo si se cumplen **todos** los puntos. El agente verifica con el comando/método indicado y lo copia a `INFORME_FINAL.md`.

## A. Proceso

- [ ] `PROGRESO.md` no tiene tareas `- [ ]` salvo las marcadas Bloqueado con motivo.
- [ ] Cada tarea hecha tiene commit convencional que cita el ID.
- [ ] `./gradlew :app:assembleRelease testReleaseUnitTest lintVitalRelease` exit 0.

## B. Req 1 — Calidad y velocidad

- [ ] Release `isMinifyEnabled == true` y `isShrinkResources == true` (`app/build.gradle.kts`).
- [ ] Hay splits ABI o se documenta por qué no (P02-T09).
- [ ] APK after < APK baseline (METRICAS_BASE).
- [ ] Room catálogo tiene índices (schema v≥5).
- [ ] OkHttp tiene Cache.
- [ ] Home/Search/Category no cargan el catálogo entero sin límite.
- [ ] Lazy lists de apps usan `key`.
- [ ] Cada pantalla de P08-T09 tiene empty/error/loading (skeleton donde hay listas).

## C. Req 2 — Updates sin mirrors

- [ ] `rg -i 'ApkPureClient|ApkComboClient' --glob '*.kt'` vacío.
- [ ] `rg mirrorVersionsFor --glob '*.kt'` vacío.
- [ ] `rg 'Stage.MIRRORS' --glob '*.kt'` vacío.
- [ ] Settings no muestra APKPure/APKCombo.
- [ ] Scan solo itera `enabledProviders` + Play si toggle on.
- [ ] WorkManager periodic existe; BootReceiver re-arma scan.
- [ ] Canales INSTALLATION y ERRORS se usan o se eliminaron.

## D. Req 3 — Fuentes

- [ ] Existe `AppSourceProvider` y al menos implementaciones: F-Droid index, GitHub, Gitea/GitLab/Codeberg, HTML+regex.
- [ ] `docs/source-providers.md` cita clases que **existen**.
- [ ] UI add/edit/enable/reorder/delete+confirm.
- [ ] `ensureBuiltIns` no pisa `priority`.
- [ ] Import/export JSON roundtrip test.
- [ ] Preferred source por app.
- [ ] Dedup catálogo alineada con updates (prioridad → version).

## E. Req 4 — Identidad (P10 omitido)

- [ ] Nombre visible, iconos, User-Agent y clases `NovaStore*` **sin** renombrar.
- [ ] `applicationId` sigue `com.novastore.fork`.

## F. Req 5 — Diseño

- [ ] Tokens Spacing/Shape/Color en `core/ui/theme`.
- [ ] `AccentPalette` ≤ 6 entradas.
- [ ] Temas light/dark/AMOLED/dynamic.
- [ ] Home, Search, Details, Downloads, Updates, Installed, Settings usan componentes tokenizados (0 `Color(0x` en esos Screens).

## G. Req 6 — Settings

- [ ] Secciones: Fuentes, Actualizaciones, Descargas e instalación, Apariencia, Almacenamiento y caché, Privacidad, Copia de seguridad, Acerca de.
- [ ] Búsqueda de ajustes.
- [ ] Confirmación en borrados.
- [ ] `SettingsScreen.kt` ≤ 250 L.

## H. Seguridad y a11y

- [ ] HTTP artifact download rechazado.
- [ ] `network_security_config` cleartext false.
- [ ] SHA-256 de índice F-Droid mandatory; auto-update no instala HTML sin hash.
- [ ] Firma: no skip si digest instalado null.
- [ ] Session plaintext migrado.
- [ ] Targets 48 dp en los 6 sitios del audit UI.
- [ ] `MANAGE_EXTERNAL_STORAGE` ausente si el cache interno basta.

## I. Identidad de mirrors residual

Comando final:

```
rg -i 'apkpure|apkcombo' --glob '*.kt' --glob '*.xml'
```

Permitido únicamente: `StoreLinks` parse de URL de usuario y tests de ese parse. Cualquier cliente HTTP a esos hosts = **no terminado**.

## J. Entregable

- [ ] `planes de implementacion/INFORME_FINAL.md` con métricas before/after y SHA.
