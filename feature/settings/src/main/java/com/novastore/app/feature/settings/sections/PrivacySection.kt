package com.novastore.app.feature.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.novastore.app.core.model.DeviceProfile
import com.novastore.app.core.ui.R as UiR
import com.novastore.app.domain.repository.AccountState
import com.novastore.app.feature.settings.SettingsUiState
import com.novastore.app.feature.settings.SettingsViewModel

/**
 * Privacy — the Google Play account, device profile, session provider,
 * why Nova Store sees your apps, notifications and sign out.
 */
@Composable
internal fun PrivacySection(
    show: (String) -> Boolean,
    state: SettingsUiState,
    viewModel: SettingsViewModel,
    onOpenAccount: () -> Unit,
    onSessionProvider: () -> Unit,
    onNotificationsToggled: (Boolean) -> Unit,
) {
    var showSignOut by remember { mutableStateOf(false) }
    val signedIn = state.accountState is AccountState.SignedIn

    SettingsSection(title = stringResource(UiR.string.settings_privacy_section)) {
        if (show("play_account")) {
            AccountStateRow(
                accountState = state.accountState,
                onOpenAccount = onOpenAccount,
            )
            SwitchRow(description = stringResource(UiR.string.settings_play_updates_desc),
                label = stringResource(UiR.string.settings_play_updates),
                checked = state.playUpdatesEnabled,
                onChecked = viewModel::setPlayUpdatesEnabled,
            )
        }
        if (show("device_profile")) {
            DeviceProfileDropdown(
                profiles = state.deviceProfiles,
                selectedFile = state.selectedDeviceProfile,
                onSelect = viewModel::setPlayDeviceProfile,
            )
        }
        if (show("session_provider")) {
            RowDivider()
            SourceNavRow(
                icon = Icons.Filled.Link,
                label = stringResource(UiR.string.sources_session_provider),
                description = stringResource(
                    if (state.tokenDispenserUrl.isBlank()) {
                        UiR.string.session_provider_status_off
                    } else {
                        UiR.string.session_provider_status_on
                    },
                ),
                expanded = false,
                onClick = onSessionProvider,
            )
        }
        if (show("query_all_packages")) {
            RowDivider()
            InfoRow(
                icon = Icons.Filled.Info,
                title = stringResource(UiR.string.settings_query_all_packages),
                description = stringResource(UiR.string.settings_query_all_packages_desc),
            )
        }
        if (show("notifications")) {
            RowDivider()
            SwitchRow(description = stringResource(UiR.string.settings_notifications_desc),
                label = stringResource(UiR.string.settings_notifications),
                checked = state.settings.notificationsEnabled,
                onChecked = onNotificationsToggled,
            )
        }
        if (signedIn && show("sign_out")) {
            RowDivider()
            SourceNavRow(
                icon = Icons.AutoMirrored.Filled.Logout,
                label = stringResource(UiR.string.account_sign_out),
                description = stringResource(UiR.string.settings_sign_out_desc),
                expanded = false,
                onClick = { showSignOut = true },
            )
        }
    }

    if (showSignOut) {
        ConfirmActionDialog(
            title = stringResource(UiR.string.account_sign_out),
            message = stringResource(UiR.string.settings_sign_out_confirm),
            confirmLabel = stringResource(UiR.string.account_sign_out),
            onConfirm = {
                showSignOut = false
                viewModel.signOutPlay()
            },
            onDismiss = { showSignOut = false },
        )
    }
}

@Composable
internal fun AccountStateRow(
    accountState: AccountState,
    onOpenAccount: () -> Unit,
) {
    Surface(
        onClick = onOpenAccount,
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
                Icons.Filled.Security,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when (accountState) {
                        is AccountState.SignedIn -> accountState.email
                        AccountState.Anonymous -> stringResource(UiR.string.settings_fdroid_only)
                        AccountState.NotSignedIn -> stringResource(UiR.string.settings_not_signed_in)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = when (accountState) {
                        is AccountState.SignedIn -> stringResource(UiR.string.settings_account_manage)
                        AccountState.Anonymous -> stringResource(UiR.string.settings_account_anonymous_sub)
                        AccountState.NotSignedIn -> stringResource(UiR.string.settings_account_not_signed_sub)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun DeviceProfileDropdown(
    profiles: List<DeviceProfile>,
    selectedFile: String,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = profiles.firstOrNull { it.fileName == selectedFile }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = selected?.displayName ?: selectedFile.removeSuffix(".properties"),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(UiR.string.settings_device_profile)) },
            leadingIcon = { Icon(Icons.Filled.Smartphone, contentDescription = null) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
            supportingText = { Text(stringResource(UiR.string.settings_device_profile_hint)) },
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            profiles.forEach { profile ->
                DropdownMenuItem(
                    text = { Text(profile.displayName) },
                    leadingIcon = {
                        if (profile.fileName == selectedFile) {
                            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    },
                    onClick = {
                        onSelect(profile.fileName)
                        expanded = false
                    },
                )
            }
        }
    }
}

/** Editor for the optional session provider (token dispenser) endpoint. */
@Composable
internal fun SessionProviderDialog(
    initial: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var draft by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(UiR.string.sources_session_provider)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    label = { Text(stringResource(UiR.string.session_provider_label)) },
                    placeholder = { Text(stringResource(UiR.string.session_provider_placeholder)) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    stringResource(UiR.string.session_provider_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(draft) }) { Text(stringResource(UiR.string.action_save)) }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { draft = "" }) { Text(stringResource(UiR.string.action_reset)) }
                TextButton(onClick = onDismiss) { Text(stringResource(UiR.string.action_cancel)) }
            }
        },
    )
}