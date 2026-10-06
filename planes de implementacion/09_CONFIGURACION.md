# P09 — Rediseño de Configuración

## Objetivo

Settings buscable, agrupada, con descripciones, defaults sensatos, confirmación destructiva. Blob 1608 L partido.

## Prerrequisitos

P06 (fuentes UI). P07 (componentes). P05 (sin mirrors). P08-T10 no bloquea.

## Archivos afectados

- `feature/settings/SettingsScreen.kt`
- `feature/settings/SettingsViewModel.kt`
- extraer: `feature/settings/sections/SourcesSection.kt`, `UpdatesSection.kt`, `DownloadsSection.kt`, `AppearanceSection.kt`, `StorageSection.kt`, `PrivacySection.kt`, `BackupSection.kt`, `AboutSection.kt`, `SettingsSearch.kt`
- `core/datastore/SettingsDataStore.kt` (defaults)
- `core/model/UpdateSettings.kt` (defaults L8 región)
- strings values/es/fr/ru

## Estructura prescrita (orden)

1. Fuentes  
2. Actualizaciones  
3. Descargas e instalación  
4. Apariencia  
5. Almacenamiento y caché  
6. Privacidad  
7. Copia de seguridad  
8. Acerca de  

Cuenta Play: **dentro de Privacidad** o enlace a ruta `account` — prescrito: fila “Cuenta Play” al inicio de Privacidad que navega a `account` (no embeber 200 L de login en Settings).

Búsqueda: filtra filas por título/descripcion (`remember` list of `SettingsItem(id, title, keywords, section)`).

## Tareas

### P09-T01 Reagrupar secciones

**Qué:** Reordenar UI al listado. Mover Appearance fuera del inicio. Quitar Advanced cajón: repartir (root install → Descargas e instalación; token dispenser → Privacidad).

**Aceptación:** grep headers; el orden de `SectionTitle` coincide.

**Verificación:** lectura SettingsScreen/sections.

### P09-T02 Búsqueda ajustes

**Qué:** `NovaSearchBar` top. Query filtra items; secciones vacías se ocultan. Sin match: EmptyState.

**Aceptación:** buscar “wifi” muestra la fila Wi-Fi only.

**Verificación:** test VM mapping keywords o test Compose omitido si no hay infra — entonces prueba manual documentada en commit.

### P09-T03 Descripciones y defaults

**Qué:** Cada SwitchRow tiene `title` + `description`. Defaults ya en `UpdateSettings`: auto off, wifi on, confirm install on, play on, anonymous on. Documentar en About “valores de fábrica”. Añadir botón Reset settings (P09-T04 confirm).

**Aceptación:** 0 toggle sin description.

**Verificación:** revisar composables SwitchRow.

### P09-T04 Destructivas

**Qué:** Confirm: borrar fuente, reset settings, borrar caché, borrar descargas, sign out (si se mueve). `Downloads auto clean` no es destructivo inmediato.

**Aceptación:** cada acción que borra datos tiene Dialog.

**Verificación:** rg `clear`/`remove`/`reset` en settings.

### P09-T05 Privacidad / backup / about

**Qué:** Privacidad: cuenta Play link, anonymous mode, QUERY_ALL_PACKAGES explicación, notificaciones. Backup: export/import fuentes (P06-T05) + export settings JSON opcional (mismo archivo versioned o dos files). About: versionName, licenses, no-affiliation Google (README L164).

**Aceptación:** secciones existen; import/export visible.

**Verificación:** compile.

### P09-T06 Partir blob

**Qué:** `SettingsScreen.kt` ≤ 250 L (scaffold + search + lista de secciones). Lógica en VM.

**Aceptación:** `wc -l SettingsScreen.kt` ≤ 250.

**Verificación:** wc -l.

## Definición de hecho

8 secciones, search, confirms, archivo principal delgado, 0 mirrors, fuentes con reorder.

## Notas de decisión

No Settings Compose `PreferenceScreen` XML. Todo Compose. No tercer nivel de navegación innecesario: secciones collapsible en una columna.
