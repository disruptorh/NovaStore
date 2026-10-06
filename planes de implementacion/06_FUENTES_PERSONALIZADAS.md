# P06 — Fuentes personalizadas (UI + lógica)

## Objetivo

UX tipo Obtainium: añadir/editar/activar/ordenar/borrar fuentes; validar URL; preview; import/export JSON; catálogo único con fuente preferida por app.

## Prerrequisitos

P04 (providers). P05 (sin toggles mirror en Settings). P07 no obligatorio; usar componentes actuales y re-skin en P09 si P07 no terminó. Si P07-T05 listo, usar Dialog/Sheet tokens.

## Archivos afectados

- `feature/settings/SettingsScreen.kt` (repos ~312, ~1182, ~1416)
- `feature/settings/SettingsViewModel.kt` (~325 add)
- `domain/.../RepositoriesRepository.kt` (añadir `update`, `setPriority`, `import`, `export`)
- `data/.../RepositoriesRepositoryImpl.kt` especialmente `ensureBuiltIns` ~L299
- `core/database/.../dao/RepositoryDao.kt`
- `feature/details/AppDetailsScreen.kt` / ViewModel (preferred source)
- `feature/updates/UpdatesViewModel.kt` `chooseSource`
- strings `core/ui/src/main/res/values/strings.xml` (+ es/fr/ru)
- nuevo: `data/.../source/SourceBackup.kt` JSON kotlinx.serialization

## Tareas

### P06-T01 Diálogo add/edit

**Qué:** Diálogo: nombre, tipo (`ProviderType`), URL, campos extra (regex, proyecto Gitea). Editar custom; built-in solo nombre local opcional (URL locked). Validación síncrona de esquema HTTPS.

**Aceptación:** no se inserta row si URL inválida. Edit persiste.

**Verificación:** compile; prueba manual o test VM.

### P06-T02 Preview

**Qué:** Botón “Probar” llama `provider.validate`. Muestra sampleNames o error. No escribe catálogo hasta Confirmar.

**Aceptación:** 404 muestra error; éxito lista ≤5 apps.

**Verificación:** test ViewModel fake provider.

### P06-T03 Enable/delete + confirmación

**Qué:** Switch enable. Delete: `AlertDialog` “Se quitarán N apps del catálogo”. Built-in: delete = disable (comportamiento actual `remove`) + texto claro. Custom: borra row + `clearSource`.

**Aceptación:** no hay delete sin diálogo (`SettingsScreen` región lista repos).

**Verificación:** grep `onRemove` + Dialog.

### P06-T04 Reorder prioridad

**Qué:** `RepositoryDao.updatePriority(id, priority)`. API `reorder(idsInOrder: List<String>)` asigna 10,20,30… `ensureBuiltIns` **solo inserta missing**; **nunca** `UPDATE priority` de filas existentes.

**Aceptación:** matar proceso: orden se mantiene. Test DAO.

**Verificación:** test `ensureBuiltIns_doesNotResetPriority`; UI drag o botones up/down (drag Compose opcional; botones suficientes).

**Riesgo:** built-ins nuevos no aparecen. Mitigación: insert if absent only.

### P06-T05 Import/export JSON

**Qué:** Schema:

```json
{
  "version": 1,
  "sources": [
    {
      "name": "F-Droid",
      "url": "https://f-droid.org/repo",
      "type": "FDROID_INDEX",
      "enabled": true,
      "priority": 10,
      "extra": null
    }
  ]
}
```

Export: `ACTION_CREATE_DOCUMENT`. Import: merge por URL (no duplicar), no borrar built-ins ausentes en el archivo. Rechazar http.

**Aceptación:** roundtrip test serialización. Import de 2 custom + 1 fdroid enabled=false respeta enabled.

**Verificación:** `SourceBackupTest`.

### P06-T06 Fuente preferida en detalle

**Qué:** Si hay ≥2 sources para el package, chips en `AppDetailsScreen`. Persistir `SettingsDataStore.setPreferredSource`. Updates usa esa fuente (ya `chooseSource`). Instalar usa artefacto de esa fuente.

**Aceptación:** elegir GitHub vs F-Droid cambia version list.

**Verificación:** VM test.

### P06-T07 Dedup catálogo

**Qué:** `observeRecent` hoy ignora priority (`CatalogDao.kt:79-87`). Cambiar a la misma regla: una fila por package, min priority (y preferred override en capa domain si aplica). Search `distinctBy packageName` ya asume order — documentar ORDER BY priority.

**Aceptación:** misma app en 2 repos: home/search/details header coinciden con min priority salvo preferida.

**Verificación:** SQL test o instrumentado.

### P06-T08 Integridad al añadir

**Qué:** Preview warning si el índice no trae sha256 (`SourcePreview.warning`). HTML provider siempre warning: “sin hash; se calculará post-descarga y no hay checksum de origen”. No bloquear add.

**Aceptación:** string visible en diálogo.

**Verificación:** preview fixture sin sha256.

## Definición de hecho

CRUD fuentes completo, reorder persistente, JSON I/O, preview, preferred source, dedup consistente. Toggles github_catalog_enabled/gitlab_catalog_enabled eliminados (reemplazados por fuentes).

## Notas de decisión

No auto-importar “catálogo GitHub trending”. Obtainium = URL explícita.
