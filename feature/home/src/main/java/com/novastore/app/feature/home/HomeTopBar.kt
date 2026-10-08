package com.novastore.app.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.novastore.app.core.model.HomeLayoutStyle
import com.novastore.app.core.model.IconSize
import com.novastore.app.core.ui.R as UiR
import com.novastore.app.core.ui.theme.BrandIndigo
import com.novastore.app.core.ui.theme.NovaElevation
import com.novastore.app.core.ui.theme.NovaShapes
import com.novastore.app.core.ui.theme.NovaSpacing
import com.novastore.app.core.ui.theme.NovaTitleBrush

// ----------------------------------------------------------------------
// Top bar
// ----------------------------------------------------------------------

@Composable
internal fun HomeTopBar(
    state: HomeUiState,
    onOpenSearch: () -> Unit,
    onScan: () -> Unit,
    onOpenSettings: () -> Unit,
    onStyle: (HomeLayoutStyle) -> Unit,
    onColumns: (Int) -> Unit,
    onIconSize: (IconSize) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Nova Store",
                style = MaterialTheme.typography.headlineMedium.copy(
                    brush = NovaTitleBrush,
                    fontWeight = FontWeight.ExtraBold,
                ),
                modifier = Modifier.weight(1f),
            )
            LayoutMenuButton(state = state, onStyle = onStyle, onColumns = onColumns, onIconSize = onIconSize)
            IconButton(onClick = onOpenSettings) {
                Icon(
                    Icons.Filled.Settings,
                    contentDescription = stringResource(UiR.string.tab_settings),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** The search pill — the store's front door. */
@Composable
internal fun HomeSearchPill(onOpenSearch: () -> Unit, onScan: () -> Unit) {
    Surface(
        onClick = onOpenSearch,
        shape = NovaShapes.Sheet,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = NovaElevation.SEARCH_BAR,
        modifier = Modifier.fillMaxWidth().height(SEARCH_PILL_HEIGHT),
    ) {
        Row(
            modifier = Modifier.padding(start = NovaSpacing.LG, end = NovaSpacing.XS),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NovaSpacing.MD),
        ) {
            Icon(
                Icons.Filled.Search,
                contentDescription = stringResource(UiR.string.cd_search),
                tint = BrandIndigo,
                modifier = Modifier.size(22.dp),
            )
            Text(
                text = stringResource(UiR.string.home_search_pill),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onScan) {
                Icon(
                    Icons.Filled.QrCodeScanner,
                    contentDescription = stringResource(UiR.string.search_scan),
                    tint = BrandIndigo,
                )
            }
        }
    }
}

/** Search pill height (the slide-away bar is [SEARCH_BAR_HEIGHT] with spacing). */
private val SEARCH_PILL_HEIGHT = 52.dp

/** Grid icon: layout (rows / grid / list), columns and icon size. */
@Composable
private fun LayoutMenuButton(
    state: HomeUiState,
    onStyle: (HomeLayoutStyle) -> Unit,
    onColumns: (Int) -> Unit,
    onIconSize: (IconSize) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                Icons.Outlined.GridView,
                contentDescription = stringResource(UiR.string.home_layout_title),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            MenuHeader(stringResource(UiR.string.home_layout_style))
            MenuCheck(stringResource(UiR.string.home_layout_shelves), state.layoutStyle == HomeLayoutStyle.SHELVES) {
                onStyle(HomeLayoutStyle.SHELVES); expanded = false
            }
            MenuCheck(stringResource(UiR.string.home_layout_grid), state.layoutStyle == HomeLayoutStyle.GRID) {
                onStyle(HomeLayoutStyle.GRID); expanded = false
            }
            MenuCheck(stringResource(UiR.string.home_layout_list), state.layoutStyle == HomeLayoutStyle.LIST) {
                onStyle(HomeLayoutStyle.LIST); expanded = false
            }
            MenuHeader(stringResource(UiR.string.home_layout_columns))
            listOf(2, 3, 4).forEach { columns ->
                MenuCheck(stringResource(UiR.string.home_layout_columns_value, columns), columns == state.gridColumns) {
                    onColumns(columns)
                }
            }
            MenuHeader(stringResource(UiR.string.home_layout_icon_size))
            IconSize.entries.forEach { size ->
                MenuCheck(
                    stringResource(
                        when (size) {
                            IconSize.SMALL -> UiR.string.icon_size_small
                            IconSize.MEDIUM -> UiR.string.icon_size_medium
                            IconSize.LARGE -> UiR.string.icon_size_large
                        },
                    ),
                    size == state.iconSize,
                ) { onIconSize(size) }
            }
        }
    }
}

@Composable
private fun MenuHeader(text: String) {
    DropdownMenuItem(
        text = { Text(text, style = MaterialTheme.typography.labelMedium, color = BrandIndigo) },
        enabled = false,
        onClick = {},
    )
}

@Composable
private fun MenuCheck(text: String, checked: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text) },
        trailingIcon = { if (checked) Text("✓", color = BrandIndigo, fontWeight = FontWeight.Bold) },
        onClick = onClick,
    )
}
