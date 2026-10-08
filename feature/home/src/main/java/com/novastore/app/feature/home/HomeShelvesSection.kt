package com.novastore.app.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.novastore.app.core.model.RemoteApp
import com.novastore.app.core.ui.R as UiR
import com.novastore.app.core.ui.components.AppIcon
import com.novastore.app.core.ui.components.NovaRatingCompact
import com.novastore.app.core.ui.components.NovaRatingRow
import com.novastore.app.core.ui.components.ShimmerBox
import com.novastore.app.core.ui.components.SourceBadge
import com.novastore.app.core.ui.theme.BrandIndigo
import com.novastore.app.core.ui.theme.HeroGradients
import kotlinx.coroutines.delay

// ----------------------------------------------------------------------
// Hero pager
// ----------------------------------------------------------------------

@Composable
internal fun HeroPager(apps: List<RemoteApp>, onOpen: (String) -> Unit) {
    val pagerState = rememberPagerState(pageCount = { apps.size })
    // Gentle auto-advance; pauses while the user is swiping.
    LaunchedEffect(pagerState, apps) {
        while (apps.size > 1) {
            delay(5_500)
            if (!pagerState.isScrollInProgress) {
                pagerState.animateScrollToPage((pagerState.currentPage + 1) % apps.size)
            }
        }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 20.dp),
            pageSpacing = 12.dp,
            key = { apps[it].packageName },
        ) { page ->
            HeroCard(app = apps[page], colors = HeroGradients[page % HeroGradients.size], onClick = { onOpen(apps[page].packageName) })
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(apps.size) { index ->
                val selected = pagerState.currentPage == index
                Box(
                    modifier = Modifier
                        .height(6.dp)
                        .width(if (selected) 18.dp else 6.dp)
                        .clip(CircleShape)
                        .background(if (selected) BrandIndigo else MaterialTheme.colorScheme.outlineVariant),
                )
            }
        }
    }
}

@Composable
private fun HeroCard(app: RemoteApp, colors: List<Color>, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(196.dp)
            .clip(RoundedCornerShape(30.dp))
            .background(Brush.linearGradient(colors))
            .drawBehind {
                // Soft decorative orbs — depth without images.
                drawCircle(Color.White.copy(alpha = 0.10f), radius = size.height * 0.75f, center = Offset(size.width * 0.95f, -size.height * 0.1f))
                drawCircle(Color.White.copy(alpha = 0.07f), radius = size.height * 0.45f, center = Offset(size.width * 0.78f, size.height * 1.05f))
            }
            .clickable(onClick = onClick)
            .padding(20.dp),
    ) {
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.White.copy(alpha = 0.25f))
                        .padding(3.dp),
                ) {
                    AppIcon(
                        packageName = app.packageName,
                        appName = app.name,
                        iconUrl = app.iconUrl,
                        size = 74.dp,
                        shape = RoundedCornerShape(21.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = app.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    (app.developer ?: app.categories.firstOrNull())?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.85f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = app.summary ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.9f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
                app.rating?.let { rating ->
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color.White.copy(alpha = 0.22f))
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        // Decorative: the numeric rating below is the label.
                        Icon(Icons.Filled.Star, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                        Text("%.1f".format(rating), style = MaterialTheme.typography.labelLarge, color = Color.White)
                    }
                    Spacer(Modifier.width(8.dp))
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color.White)
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = stringResource(UiR.string.home_hero_open),
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.first(),
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

// ----------------------------------------------------------------------
// Shelf card
// ----------------------------------------------------------------------

@Composable
internal fun ShelfCard(
    name: String,
    packageName: String,
    iconUrl: String?,
    rating: Float?,
    onClick: () -> Unit,
    iconSize: Dp = 84.dp,
) {
    // Tight lanes: the label may be ~10dp wider than the icon, no more.
    Column(
        modifier = Modifier
            .width(iconSize + 10.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppIcon(
            packageName = packageName,
            appName = name,
            iconUrl = iconUrl,
            size = iconSize,
            shape = RoundedCornerShape(iconSize / 3.4f),
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 2,
            minLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        if (rating != null) {
            Spacer(Modifier.height(2.dp))
            NovaRatingCompact(value = rating)
        }
    }
}

// ----------------------------------------------------------------------
// Loading placeholders / unused-at-runtime carousels kept from the feed
// ----------------------------------------------------------------------

@Composable
internal fun ShelfShimmerRow(iconSize: Dp) {
    Row(
        modifier = Modifier.padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(4) {
            ShimmerBox(modifier = Modifier.size(width = iconSize + 20.dp, height = iconSize + 48.dp), cornerRadius = 20.dp)
        }
    }
}

/** Compact vertical card for the "New on F-Droid" carousel. */
@Composable
internal fun RecentAppCard(app: RemoteApp, iconSize: Dp, onClick: () -> Unit) {
    val width = (iconSize * 2.1f).coerceIn(148.dp, 208.dp)
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.width(width),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Icon clamp: measured inside the 12dp-padded column, so maxWidth
            // is the card's icon-available width (card width − 24dp) — the
            // icon can never clip the card in the carousel.
            val effectiveIconSize = minOf(iconSize, width - 24.dp)
            AppIcon(
                packageName = app.packageName,
                appName = app.name,
                iconUrl = app.iconUrl,
                size = effectiveIconSize,
                shape = RoundedCornerShape(effectiveIconSize / 3.2f),
            )
            Text(
                text = app.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                minLines = 2,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                NovaRatingRow(value = app.rating, starSize = 11.dp, centered = true)
            }
            SourceBadge(source = app.source, compact = true)
        }
    }
}

@Composable
internal fun FeaturedShimmerRow() {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(2, key = { "shimmer-hero-$it" }, contentType = { "shimmer" }) {
            ShimmerBox(
                modifier = Modifier.size(width = 272.dp, height = 96.dp),
                cornerRadius = 24.dp,
            )
        }
    }
}
