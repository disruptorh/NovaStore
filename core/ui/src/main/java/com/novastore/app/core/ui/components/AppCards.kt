package com.novastore.app.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.novastore.app.core.model.RemoteApp
import com.novastore.app.core.ui.theme.NovaElevation
import com.novastore.app.core.ui.theme.NovaShapes
import com.novastore.app.core.ui.theme.NovaSpacing
import com.novastore.app.core.ui.theme.OnEmerald

/**
 * Hero carousel card: 64dp icon, name, rating and category on the accent
 * gradient. ~272dp wide, white content.
 */
@Composable
fun AppCardLarge(
    app: RemoteApp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: androidx.compose.ui.unit.Dp = 272.dp,
) {
    Box(
        modifier = modifier
            .width(width)
            .clip(NovaShapes.Sheet)
            .background(novaAccentBrush())
            .clickable(onClick = onClick)
            .padding(NovaSpacing.LG),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            AppIcon(
                packageName = app.packageName,
                appName = app.name,
                iconUrl = app.iconUrl,
                size = 64.dp,
                shape = appIconSquircle(64.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(NovaSpacing.XS),
            ) {
                Text(
                    text = app.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = OnEmerald,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = app.developer ?: app.categories.firstOrNull() ?: "",
                    style = MaterialTheme.typography.labelMedium,
                    color = OnEmerald.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                NovaRatingRow(
                    value = app.rating,
                    tint = OnEmerald,
                    numericStyle = MaterialTheme.typography.labelMedium.copy(color = OnEmerald),
                )
            }
        }
    }
}

/**
 * List row card: 52dp icon, name, developer, rating stars + size + source
 * badge on an elevated surface with 20dp corners. The 52dp icon is
 * decode-pinned as well: AppIcon applies Modifier.size to its AsyncImage
 * with the same value, so remote icons decode at display resolution.
 */
@Composable
fun AppCardRow(
    app: RemoteApp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = NovaShapes.Card,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = NovaElevation.CARDS,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = NovaSpacing.MD),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(NovaSpacing.MD),
        ) {
            AppIcon(
                packageName = app.packageName,
                appName = app.name,
                iconUrl = app.iconUrl,
                fallbackIconUrl = app.altIconUrl,
                size = 60.dp,
                shape = appIconSquircle(60.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = app.name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(NovaSpacing.SM))
                    SourceBadge(source = app.source, compact = true)
                }
                app.developer?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                app.summary?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(NovaSpacing.SM),
                ) {
                    val rating = app.rating
                    if (rating != null && rating > 0f) {
                        Text(
                            text = "%.1f".format(rating),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        NovaRatingBar(value = rating, starSize = 12.dp)
                    } else if (app.license != null || app.source != "play") {
                        Text(
                            text = androidx.compose.ui.res.stringResource(com.novastore.app.core.ui.R.string.app_card_open_source),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                    formatDownloadCount(app.downloads)?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    app.sizeBytes?.let {
                        Text(formatFileSize(it), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (!app.isFree) {
                        Text(
                            text = androidx.compose.ui.res.stringResource(com.novastore.app.core.ui.R.string.details_value_paid),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Play-Store style grid cell: transparent background, medium icon on top,
 * name underneath, rating row below. No card surface behind the icon —
 * clean rows of icons like the real stores use. The icon is clamped to the
 * measured cell width so 4-column grids on narrow screens never overflow.
 */
@Composable
fun AppGridCell(
    app: RemoteApp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Dp = 64.dp,
    columns: Int = 3,
    spacing: Dp = NovaSpacing.MD,
) {
    // Lane width from the screen width (16dp padding on both sides) — no
    // BoxWithConstraints/SubcomposeLayout per cell: those were measured
    // twice per cell on every new row during a fling (main source of jank).
    val screenWidth = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp
    val lane = (screenWidth - NovaSpacing.XXL - spacing * (columns - 1)) / columns.coerceAtLeast(1)
    val effectiveIconSize = minOf(iconSize, lane - NovaSpacing.MD).coerceAtLeast(NovaSpacing.XXL)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(NovaShapes.Card)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = NovaSpacing.SM),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppIcon(
            packageName = app.packageName,
            appName = app.name,
            iconUrl = app.iconUrl,
            size = effectiveIconSize,
            shape = appIconSquircle(effectiveIconSize),
        )
        Spacer(Modifier.height(NovaSpacing.SM))
        Text(
            text = app.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            minLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(2.dp))
        // Play-style one star + number: one icon instead of five per cell.
        NovaRatingCompact(value = app.rating)
    }
}

/**
 * Pill-shaped category chip used in the horizontal category selector.
 * Selected state uses the accent gradient.
 */
@Composable
fun CategoryChip(
    name: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = NovaShapes.Chip
    Box(
        modifier = modifier
            .clip(shape)
            .then(
                if (selected) {
                    Modifier.background(novaAccentBrush())
                } else {
                    Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = NovaSpacing.LG, vertical = 10.dp),
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) OnEmerald else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}
