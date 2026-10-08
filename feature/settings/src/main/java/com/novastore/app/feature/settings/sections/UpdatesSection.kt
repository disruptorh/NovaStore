package com.novastore.app.feature.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.novastore.app.core.model.UpdateSchedule
import com.novastore.app.core.model.UpdateSettings
import com.novastore.app.core.ui.R as UiR

/** Updates — check cadence and the network/energy guardrails. */
@Composable
internal fun UpdatesSection(
    settings: UpdateSettings,
    show: (String) -> Boolean,
    updateSettings: ((UpdateSettings) -> UpdateSettings) -> Unit,
) {
    SettingsSection(title = stringResource(UiR.string.settings_updates_section)) {
        if (show("schedule")) {
            LabelRow(stringResource(UiR.string.settings_check_updates))
            ChoiceChipGrid(
                items = UpdateSchedule.entries.map { schedule ->
                    schedule to stringResource(
                        when (schedule) {
                            UpdateSchedule.IMMEDIATELY -> UiR.string.settings_schedule_immediately
                            UpdateSchedule.DAILY -> UiR.string.settings_schedule_daily
                            UpdateSchedule.WEEKLY -> UiR.string.settings_schedule_weekly
                        },
                    )
                },
                selected = settings.schedule,
                onSelect = { schedule -> updateSettings { it.copy(schedule = schedule) } },
            )
        }
        if (show("auto_updates")) {
            RowDivider()
            SwitchRow(description = stringResource(UiR.string.settings_auto_updates_desc),
                label = stringResource(UiR.string.settings_auto_updates),
                checked = settings.automaticUpdates,
                onChecked = { enabled -> updateSettings { it.copy(automaticUpdates = enabled) } },
            )
        }
        if (show("wifi_only") || show("charging_only")) {
            RowDivider()
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                if (show("wifi_only")) {
                    ToggleTile(
                        label = stringResource(UiR.string.settings_wifi_only),
                        description = stringResource(UiR.string.settings_wifi_only_desc),
                        checked = settings.wifiOnly,
                        onChecked = { enabled -> updateSettings { it.copy(wifiOnly = enabled) } },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (show("charging_only")) {
                    ToggleTile(
                        label = stringResource(UiR.string.settings_charging_only),
                        description = stringResource(UiR.string.settings_charging_only_desc),
                        checked = settings.chargingOnly,
                        onChecked = { enabled -> updateSettings { it.copy(chargingOnly = enabled) } },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}