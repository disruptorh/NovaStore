package com.novastore.app.feature.settings.sections

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novastore.app.core.model.AppLanguage
import com.novastore.app.core.model.ThemeMode
import com.novastore.app.core.ui.R as UiR
import com.novastore.app.feature.settings.SettingsUiState
import com.novastore.app.feature.settings.SettingsViewModel

/** Appearance — home search bar, theme, accent palette and app language. */
@Composable
internal fun AppearanceSection(
    show: (String) -> Boolean,
    state: SettingsUiState,
    viewModel: SettingsViewModel,
) {
    val searchPinned by viewModel.homeSearchPinned.collectAsStateWithLifecycle()
    SettingsSection(title = stringResource(UiR.string.settings_appearance)) {
        if (show("search_pinned")) {
            SwitchRow(description = stringResource(UiR.string.settings_search_pinned_desc),
                label = stringResource(UiR.string.settings_search_pinned),
                checked = searchPinned,
                onChecked = viewModel::setHomeSearchPinned,
            )
        }
        if (show("theme")) {
            RowDivider()
            LabelRow(stringResource(UiR.string.settings_theme))
            ChoiceChipGrid(
                items = ThemeMode.entries.map { mode ->
                    mode to stringResource(
                        when (mode) {
                            ThemeMode.SYSTEM -> UiR.string.theme_system
                            ThemeMode.LIGHT -> UiR.string.theme_light
                            ThemeMode.DARK -> UiR.string.theme_dark
                            ThemeMode.AMOLED -> UiR.string.theme_amoled
                        },
                    )
                },
                selected = state.themeMode,
                onSelect = { viewModel.setThemeMode(it) },
            )
        }
        if (show("accent")) {
            RowDivider()
            LabelRow(stringResource(UiR.string.settings_accent))
            AccentSwatchRow(
                selected = state.accentPalette,
                onSelect = { viewModel.setAccentPalette(it) },
            )
        }
        if (show("language")) {
            RowDivider()
            LabelRow(stringResource(UiR.string.settings_language))
            ChoiceChipGrid(
                items = AppLanguage.entries.map { it to it.nativeName },
                selected = state.appLanguage,
                onSelect = { viewModel.setAppLanguage(it) },
            )
        }
    }
}