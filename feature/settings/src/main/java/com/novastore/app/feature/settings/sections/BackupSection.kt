package com.novastore.app.feature.settings.sections

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Upload
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.novastore.app.core.ui.R as UiR

/** Backup — export and import the source/repository list (SAF, P06-T05). */
@Composable
internal fun BackupSection(
    show: (String) -> Boolean,
    onExport: () -> Unit,
    onImport: () -> Unit,
) {
    SettingsSection(title = stringResource(UiR.string.settings_backup_section)) {
        if (show("export_sources")) {
            SourceNavRow(
                icon = Icons.Filled.Upload,
                label = stringResource(UiR.string.settings_repos_export),
                description = stringResource(UiR.string.settings_export_desc),
                expanded = false,
                onClick = onExport,
            )
        }
        if (show("import_sources")) {
            RowDivider()
            SourceNavRow(
                icon = Icons.Filled.Download,
                label = stringResource(UiR.string.settings_repos_import),
                description = stringResource(UiR.string.settings_import_desc),
                expanded = false,
                onClick = onImport,
            )
        }
    }
}