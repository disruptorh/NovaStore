package com.novastore.app.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.novastore.app.core.ui.R as UiR
import com.novastore.app.core.ui.components.CategoryChip
import com.novastore.app.core.ui.components.EmptyState
import com.novastore.app.core.ui.components.NovaCategories
import com.novastore.app.core.ui.theme.BrandIndigo
import com.novastore.app.core.ui.theme.BrandViolet
import com.novastore.app.core.ui.theme.NovaSpacing
import com.novastore.app.core.ui.theme.NovaTitleBrush
import com.novastore.app.core.ui.theme.UpToDateBrush

internal const val ROW_PLAY_HOME = "play:home"
internal const val ROW_FDROID_NEW = "fdroid:new"

@Composable
internal fun rowLabel(key: String): String = when {
    key == ROW_PLAY_HOME -> stringResource(UiR.string.home_shelf_home)
    key == ROW_FDROID_NEW -> stringResource(UiR.string.home_new_fdroid)
    PlayShelves.isShelf(key) -> PlayShelves.label(key)
    // Repository categories carry their origin: "Finance · F-Droid" is
    // never confused with Play's "Finance" row.
    else -> NovaCategories.label(key) + " · F-Droid"
}

// ----------------------------------------------------------------------
// Quick tiles
// ----------------------------------------------------------------------

@Composable
internal fun QuickTilesRow(
    updatesCount: Int,
    favoritesCount: Int,
    onUpdates: () -> Unit,
    onInstalled: () -> Unit,
    onDownloads: () -> Unit,
    onFavorites: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        QuickTile(
            icon = if (updatesCount > 0) Icons.Filled.SystemUpdateAlt else Icons.Filled.CheckCircle,
            label = stringResource(if (updatesCount > 0) UiR.string.home_tile_updates else UiR.string.home_tile_up_to_date),
            badge = updatesCount.takeIf { it > 0 }?.toString(),
            highlighted = true,
            gradient = if (updatesCount > 0) NovaTitleBrush else UpToDateBrush,
            onClick = onUpdates,
            modifier = Modifier.weight(1f),
        )
        QuickTile(
            icon = Icons.Filled.Apps,
            label = stringResource(UiR.string.home_tile_installed),
            onClick = onInstalled,
            modifier = Modifier.weight(1f),
        )
        QuickTile(
            icon = Icons.Filled.Download,
            label = stringResource(UiR.string.home_tile_downloads),
            onClick = onDownloads,
            modifier = Modifier.weight(1f),
        )
        QuickTile(
            icon = Icons.Filled.Favorite,
            label = stringResource(UiR.string.home_favorites),
            badge = favoritesCount.takeIf { it > 0 }?.toString(),
            onClick = onFavorites,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun QuickTile(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: String? = null,
    highlighted: Boolean = false,
    gradient: Brush = NovaTitleBrush,
) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier = modifier
            .height(88.dp)
            .clip(shape)
            .then(
                if (highlighted) {
                    Modifier.background(gradient)
                } else {
                    Modifier.background(MaterialTheme.colorScheme.surfaceContainer)
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(
                        if (highlighted) Color.White.copy(alpha = 0.22f) else BrandIndigo.copy(alpha = 0.14f),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                // Decorative: the tile's visible label below names the target.
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (highlighted) Color.White else BrandIndigo,
                    modifier = Modifier.size(20.dp),
                )
            }
            if (badge != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 8.dp, y = (-4).dp)
                        .clip(CircleShape)
                        .background(if (highlighted) Color.White else BrandViolet)
                        .padding(horizontal = 6.dp, vertical = 1.dp),
                ) {
                    Text(
                        text = badge,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (highlighted) BrandIndigo else Color.White,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (highlighted) Color.White else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

// ----------------------------------------------------------------------
// Section header
// ----------------------------------------------------------------------

@Composable
internal fun HomeSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onSeeAll: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (onSeeAll != null) {
            Surface(
                onClick = onSeeAll,
                shape = RoundedCornerShape(50),
                color = BrandIndigo.copy(alpha = 0.12f),
                // a11y: "See all" keeps a 48dp touch target.
                modifier = Modifier.heightIn(min = NovaSpacing.XXXL),
            ) {
                Row(
                    modifier = Modifier.padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(UiR.string.action_see_all),
                        style = MaterialTheme.typography.labelLarge,
                        color = BrandIndigo,
                    )
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = BrandIndigo,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

// ----------------------------------------------------------------------
// Category chips row (grid / list layout)
// ----------------------------------------------------------------------

@Composable
internal fun CategoryChipsRow(
    categories: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "chip-all", contentType = { "chip" }) {
            CategoryChip(
                name = stringResource(UiR.string.home_category_all),
                selected = selected == null,
                onClick = { onSelect(null) },
                // a11y: chips keep a 48dp touch target.
                modifier = Modifier.heightIn(min = NovaSpacing.XXXL),
            )
        }
        items(categories, key = { it }, contentType = { "category-chip" }) { category ->
            CategoryChip(
                name = if (PlayShelves.isShelf(category)) PlayShelves.label(category) else NovaCategories.label(category),
                selected = selected == category,
                onClick = { onSelect(category) },
                modifier = Modifier.heightIn(min = NovaSpacing.XXXL),
            )
        }
    }
}

// ----------------------------------------------------------------------
// Banners
// ----------------------------------------------------------------------

@Composable
internal fun OfflineBanner() {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.padding(horizontal = 16.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                Icons.Filled.CloudOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = stringResource(UiR.string.home_offline_banner),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
internal fun ScanProgressBanner(progress: com.novastore.app.domain.repository.ScanProgress) {
    val title = stringResource(
        when (progress.stage) {
            com.novastore.app.domain.repository.ScanProgress.Stage.REPOSITORIES -> UiR.string.scan_stage_repositories
            com.novastore.app.domain.repository.ScanProgress.Stage.INSTALLED_APPS -> UiR.string.scan_stage_installed
            com.novastore.app.domain.repository.ScanProgress.Stage.GOOGLE_PLAY -> UiR.string.scan_stage_play
        },
    )
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                if (progress.total > 0) {
                    Text(
                        "${progress.done}/${progress.total}",
                        style = MaterialTheme.typography.labelMedium,
                        color = BrandIndigo,
                    )
                }
            }
            progress.label?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            Spacer(Modifier.height(6.dp))
            if (progress.total > 0) {
                androidx.compose.material3.LinearProgressIndicator(
                    progress = { (progress.done.toFloat() / progress.total).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                    color = BrandIndigo,
                )
            } else {
                androidx.compose.material3.LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = BrandIndigo)
            }
        }
    }
}

// ----------------------------------------------------------------------
// Loading placeholders
// ----------------------------------------------------------------------

@Composable
internal fun ShelfPlaceholderRow(iconSize: androidx.compose.ui.unit.Dp, height: androidx.compose.ui.unit.Dp) {
    Row(
        modifier = Modifier.height(height).padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        repeat(4) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(iconSize + 10.dp)) {
                Box(
                    Modifier
                        .size(iconSize)
                        .clip(RoundedCornerShape(iconSize / 3.4f))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                )
                Spacer(Modifier.height(10.dp))
                Box(
                    Modifier
                        .size(width = iconSize * 0.8f, height = 10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                )
            }
        }
    }
}

// ----------------------------------------------------------------------
// Empty catalog
// ----------------------------------------------------------------------

/**
 * Empty catalog: not a single source returned an app. The action opens the
 * sources section of Settings — adding (or enabling) a source is the only
 * way out of an empty catalog.
 */
@Composable
internal fun CatalogEmptyState(onAddSource: () -> Unit) {
    EmptyState(
        icon = Icons.Filled.Apps,
        title = stringResource(UiR.string.home_catalog_empty_title),
        description = stringResource(UiR.string.home_catalog_empty_desc),
        actionLabel = stringResource(UiR.string.home_catalog_empty_add_source),
        onAction = onAddSource,
    )
}
