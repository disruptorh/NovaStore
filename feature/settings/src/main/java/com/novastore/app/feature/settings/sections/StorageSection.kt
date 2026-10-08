package com.novastore.app.feature.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novastore.app.core.ui.R as UiR
import com.novastore.app.feature.settings.SettingsViewModel

/** Storage & cache — auto-clean age, All-files access, destructive housekeeping. */
@Composable
internal fun StorageSection(
    show: (String) -> Boolean,
    viewModel: SettingsViewModel,
) {
    val autoCleanDays by viewModel.downloadsAutoCleanDays.collectAsStateWithLifecycle()
    var showClearCache by remember { mutableStateOf(false) }
    var showClearDownloads by remember { mutableStateOf(false) }

    SettingsSection(title = stringResource(UiR.string.settings_storage_cache_section)) {
        if (show("autoclean")) {
            AutoCleanDaysRow(days = autoCleanDays, onSelect = viewModel::setDownloadsAutoCleanDays)
        }
        if (show("clear_cache")) {
            RowDivider()
            SourceNavRow(
                icon = Icons.Filled.DeleteSweep,
                label = stringResource(UiR.string.settings_clear_cache),
                description = stringResource(UiR.string.settings_clear_cache_desc),
                expanded = false,
                onClick = { showClearCache = true },
            )
        }
        if (show("clear_downloads")) {
            RowDivider()
            SourceNavRow(
                icon = Icons.Filled.DeleteForever,
                label = stringResource(UiR.string.settings_clear_downloads),
                description = stringResource(UiR.string.settings_clear_downloads_desc),
                expanded = false,
                onClick = { showClearDownloads = true },
            )
        }
    }

    if (showClearCache) {
        ConfirmActionDialog(
            title = stringResource(UiR.string.settings_clear_cache),
            message = stringResource(UiR.string.settings_clear_cache_confirm),
            confirmLabel = stringResource(UiR.string.settings_clear_cache),
            onConfirm = {
                showClearCache = false
                viewModel.clearCache()
            },
            onDismiss = { showClearCache = false },
        )
    }
    if (showClearDownloads) {
        ConfirmActionDialog(
            title = stringResource(UiR.string.settings_clear_downloads),
            message = stringResource(UiR.string.settings_clear_downloads_confirm),
            confirmLabel = stringResource(UiR.string.settings_clear_downloads),
            onConfirm = {
                showClearDownloads = false
                viewModel.clearDownloads()
            },
            onDismiss = { showClearDownloads = false },
        )
    }
}

/** Value row for the completed-download auto-cleanup age (Off / 1 / 3 / 7 / 14 days). */
@Composable
internal fun AutoCleanDaysRow(days: Int, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Surface(
            onClick = { expanded = true },
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    Icons.Filled.DeleteSweep,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(UiR.string.settings_autoclean),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = stringResource(UiR.string.settings_autoclean_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                // Visual-only value chip; the whole row opens the dropdown.
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.height(36.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = autoCleanDaysLabel(days),
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                        )
                        Icon(
                            imageVector = Icons.Filled.ArrowDropDown,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            listOf(0, 1, 3, 7, 14).forEach { option ->
                DropdownMenuItem(
                    text = { Text(autoCleanDaysLabel(option)) },
                    leadingIcon = if (option == days) {
                        {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    } else {
                        null
                    },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}

@Composable
internal fun autoCleanDaysLabel(days: Int): String =
    if (days <= 0) {
        stringResource(UiR.string.settings_autoclean_off)
    } else {
        stringResource(UiR.string.settings_autoclean_days_fmt, days)
    }