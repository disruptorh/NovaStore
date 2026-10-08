package com.novastore.app.feature.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.novastore.app.core.model.InstallationMode
import com.novastore.app.core.model.RootAccessState
import com.novastore.app.core.ui.R as UiR
import com.novastore.app.feature.settings.SettingsUiState
import com.novastore.app.feature.settings.SettingsViewModel

/** Downloads & installation — installer mode, root status and transfer policy. */
@Composable
internal fun DownloadsSection(
    state: SettingsUiState,
    show: (String) -> Boolean,
    viewModel: SettingsViewModel,
) {
    SettingsSection(title = stringResource(UiR.string.settings_downloads_install_section)) {
        if (show("installation_mode")) {
            InstallationModeDropdown(
                selected = state.settings.installationMode,
                onSelect = { mode -> viewModel.updateSettings { it.copy(installationMode = mode) } },
            )
        }
        if (show("root_status")) {
            RootStatusSection(state, viewModel)
        }
        if (show("mobile_data") || show("download_charging")) {
            RowDivider()
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                if (show("mobile_data")) {
                    ToggleTile(
                        label = stringResource(UiR.string.settings_mobile_data),
                        description = stringResource(UiR.string.settings_mobile_data_desc),
                        checked = state.settings.mobileDataAllowed,
                        onChecked = { enabled -> viewModel.updateSettings { it.copy(mobileDataAllowed = enabled) } },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (show("download_charging")) {
                    ToggleTile(
                        label = stringResource(UiR.string.settings_download_charging),
                        description = stringResource(UiR.string.settings_download_charging_desc),
                        checked = state.settings.downloadWhileCharging,
                        onChecked = { enabled -> viewModel.updateSettings { it.copy(downloadWhileCharging = enabled) } },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        // The confirmation toggle: part of installation flow (P09 rework).
        if (show("confirm_install")) {
            RowDivider()
            ToggleTile(
                label = stringResource(UiR.string.settings_confirm),
                description = stringResource(UiR.string.settings_confirm_desc),
                checked = state.settings.confirmBeforeInstallation,
                onChecked = { enabled -> viewModel.updateSettings { it.copy(confirmBeforeInstallation = enabled) } },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (show("simultaneous")) {
            Column {
                Text(
                    stringResource(UiR.string.settings_simultaneous, state.settings.maxConcurrentDownloads),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Slider(
                    value = state.settings.maxConcurrentDownloads.toFloat(),
                    onValueChange = { value ->
                        viewModel.updateSettings { it.copy(maxConcurrentDownloads = value.toInt()) }
                    },
                    valueRange = 1f..4f,
                    steps = 2,
                )
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun InstallationModeDropdown(
    selected: InstallationMode,
    onSelect: (InstallationMode) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = stringResource(installationModeLabel(selected))
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(UiR.string.settings_installation)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            InstallationMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = { Text(stringResource(installationModeLabel(mode))) },
                    leadingIcon = {
                        if (mode == selected) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    },
                    onClick = {
                        onSelect(mode)
                        expanded = false
                    },
                )
            }
        }
    }
}

private fun installationModeLabel(mode: InstallationMode): Int = when (mode) {
    InstallationMode.AUTOMATIC -> UiR.string.settings_mode_automatic
    InstallationMode.STANDARD -> UiR.string.settings_mode_standard
    InstallationMode.ROOT -> UiR.string.settings_mode_root
    InstallationMode.MANAGED -> UiR.string.settings_mode_managed
}

@Composable
internal fun RootStatusSection(state: SettingsUiState, viewModel: SettingsViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(stringResource(UiR.string.settings_root_installation), style = MaterialTheme.typography.bodyMedium)
        val statusText = when (state.rootState) {
            RootAccessState.UNAVAILABLE -> stringResource(UiR.string.settings_root_unavailable)
            RootAccessState.AVAILABLE -> stringResource(UiR.string.settings_root_available)
            RootAccessState.AUTHORIZATION_REQUIRED -> stringResource(UiR.string.settings_root_pending)
            RootAccessState.AUTHORIZED -> stringResource(UiR.string.settings_root_authorized)
            RootAccessState.DENIED -> stringResource(UiR.string.settings_root_denied)
            RootAccessState.REVOKED -> stringResource(UiR.string.settings_root_revoked)
            RootAccessState.ERROR -> stringResource(UiR.string.settings_root_error)
        }
        Text(
            text = statusText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (state.rootState != RootAccessState.AUTHORIZED) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { viewModel.checkRoot() }, enabled = !state.busy) {
                    Text(
                        stringResource(UiR.string.settings_root_check),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (state.busy) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.5.dp)
                }
            }
        }
    }
}