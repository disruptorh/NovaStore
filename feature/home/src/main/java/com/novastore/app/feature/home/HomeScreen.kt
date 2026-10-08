package com.novastore.app.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novastore.app.core.model.HomeLayoutStyle
import com.novastore.app.core.model.IconSize
import com.novastore.app.core.model.RemoteApp
import com.novastore.app.core.ui.R as UiR
import com.novastore.app.core.ui.components.AppCardRow
import com.novastore.app.core.ui.components.AppGridCell
import com.novastore.app.core.ui.components.NovaCategories
import com.novastore.app.core.ui.components.ShimmerBox
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenUpdates: () -> Unit,
    onOpenAppDetails: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAccount: () -> Unit = {},
    onOpenInstalled: () -> Unit = {},
    onOpenDownloads: () -> Unit = {},
    onOpenCategory: (String) -> Unit = {},
    onOpenSearchQuery: (String) -> Unit = {},
    onAddSource: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val startScan = com.novastore.app.core.ui.components.rememberQrScanner { raw ->
        viewModel.resolveScanned(raw) { link ->
            when (link) {
                is com.novastore.app.core.model.StoreLink.App -> onOpenAppDetails(link.packageName)
                is com.novastore.app.core.model.StoreLink.Search -> onOpenSearchQuery(link.query)
                null -> Unit
            }
        }
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val searchPinned by viewModel.searchPinned.collectAsStateWithLifecycle()

    // --- Search bar that slides under the header while scrolling ---
    // Offset in px: 0 = fully shown, -searchBarPx = hidden under the header.
    // Driven by nested scroll (the list keeps all of its scroll), drawn via
    // graphicsLayer — no relayout of the feed on any frame.
    val density = androidx.compose.ui.platform.LocalDensity.current
    val searchBarPx = with(density) { SEARCH_BAR_HEIGHT.toPx() }
    val searchOffset = remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    LaunchedEffect(searchPinned) { if (searchPinned) searchOffset.floatValue = 0f }
    val collapseConnection = remember(searchPinned, searchBarPx) {
        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
            override fun onPreScroll(
                available: androidx.compose.ui.geometry.Offset,
                source: androidx.compose.ui.input.nestedscroll.NestedScrollSource,
            ): androidx.compose.ui.geometry.Offset {
                if (!searchPinned) {
                    searchOffset.floatValue = (searchOffset.floatValue + available.y).coerceIn(-searchBarPx, 0f)
                }
                return androidx.compose.ui.geometry.Offset.Zero
            }

            override suspend fun onPostFling(
                consumed: androidx.compose.ui.unit.Velocity,
                available: androidx.compose.ui.unit.Velocity,
            ): androidx.compose.ui.unit.Velocity {
                // Never stop half-way: settle fully shown or fully hidden.
                if (!searchPinned) {
                    val current = searchOffset.floatValue
                    val target = if (current < -searchBarPx / 2) -searchBarPx else 0f
                    if (current != target) {
                        androidx.compose.animation.core.animate(current, target) { value, _ -> searchOffset.floatValue = value }
                    }
                }
                return androidx.compose.ui.unit.Velocity.Zero
            }
        }
    }
    val scanProgress by viewModel.scanProgress.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // --- Scroll-gated browse content (see git history for the rationale) ---
    val browseContentPair = remember {
        // Seeded with the CURRENT content: re-entering Home from a details
        // page must not start from an empty list (that clamped the restored
        // scroll position back to the top).
        mutableStateOf(state.categoryApps to state.canLoadMore)
    }
    val incomingPair = state.categoryApps to state.canLoadMore
    LaunchedEffect(incomingPair) {
        val current = browseContentPair.value.first
        val isAppend = current.isNotEmpty() && incomingPair.first.size > current.size &&
            incomingPair.first.subList(0, current.size) == current
        // Appends (load-more) apply immediately; only a REPLACEMENT of
        // visible content waits for the fling to finish.
        if (current.isNotEmpty() && !isAppend) {
            snapshotFlow { listState.isScrollInProgress }
                .distinctUntilChanged()
                .first { scrolling -> !scrolling }
        }
        if (browseContentPair.value != incomingPair) {
            browseContentPair.value = incomingPair
        }
    }
    val browseApps: List<RemoteApp> = browseContentPair.value.first
    val browseCanLoadMore: Boolean = browseContentPair.value.second
    val browseRows = remember(browseApps, state.gridColumns) { browseApps.chunked(state.gridColumns) }
    // Nothing to show and nothing is loading: every source returned zero apps.
    val catalogEmpty = browseApps.isEmpty() && !state.loading

    val nearListEnd by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = listState.layoutInfo.totalItemsCount
            total > 4 && last >= total - 8
        }
    }
    LaunchedEffect(nearListEnd, browseCanLoadMore, state.layoutStyle) {
        if (state.layoutStyle != HomeLayoutStyle.SHELVES && nearListEnd && browseCanLoadMore) viewModel.loadMore()
    }

    // Index of the "All apps" header, recorded while the list is built, so
    // a shelf's "See all" can jump straight to the grid.
    val browseIndex = remember { intArrayOf(0) }
    val scrollToBrowse: () -> Unit = {
        scope.launch { listState.animateScrollToItem(browseIndex[0]) }
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        HomeTopBar(
            state = state,
            onOpenSearch = onOpenSearch,
            onScan = startScan,
            onOpenSettings = onOpenSettings,
            onStyle = viewModel::setLayoutStyle,
            onColumns = viewModel::setGridColumns,
            onIconSize = viewModel::setIconSize,
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                // The bar slides out at this edge — i.e. under the header.
                .clipToBounds()
                .nestedScroll(collapseConnection),
        ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = SEARCH_BAR_HEIGHT + 4.dp,
                // No bottom bar anymore: keep the last row above the system
                // navigation bar (gesture pill / 3-button bar).
                bottom = 28.dp + androidx.compose.foundation.layout.WindowInsets.navigationBars
                    .asPaddingValues().calculateBottomPadding(),
            ),
            verticalArrangement = Arrangement.spacedBy(if (state.layoutStyle == HomeLayoutStyle.SHELVES) 6.dp else 18.dp),
        ) {
            var count = 0

            // --- Quick access tiles (replace the old "updates" banner) ---
            item(key = "tiles", contentType = { "tiles" }) {
                QuickTilesRow(
                    updatesCount = state.updatesCount,
                    favoritesCount = favorites.size,
                    onUpdates = onOpenUpdates,
                    onInstalled = onOpenInstalled,
                    onDownloads = onOpenDownloads,
                    onFavorites = {
                        if (favorites.isNotEmpty()) scope.launch { listState.animateScrollToItem(favoritesIndexOf(state)) }
                    },
                )
            }
            count++

            // Why the first start takes a while: every repository index is
            // being downloaded — shown right here, not only in a notification.
            scanProgress?.let { progress ->
                item(key = "scan-progress", contentType = { "banner" }) { ScanProgressBanner(progress) }
                count++
            }
            if (state.offline) {
                item(key = "offline", contentType = { "banner" }) { OfflineBanner() }
                count++
            }
            if (state.error != null) {
                item(key = "error", contentType = { "banner" }) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(16.dp),
                        // a11y: the dismissible banner keeps a 48dp touch target.
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).heightIn(min = 48.dp),
                        onClick = viewModel::dismissError,
                    ) {
                        Text(
                            text = state.error ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }
                count++
            }

            // --- Hero: "Recommended for you" pager ---
            if (state.loading && state.featured.isEmpty()) {
                item(key = "hero-shimmer", contentType = { "shimmer" }) {
                    ShimmerBox(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(196.dp),
                        cornerRadius = 30.dp,
                    )
                }
                count++
            } else if (state.featured.isNotEmpty()) {
                item(key = "hero-title", contentType = { "section-title" }) {
                    HomeSectionHeader(
                        title = stringResource(UiR.string.home_recommended),
                        subtitle = stringResource(UiR.string.home_recommended_sub),
                    )
                }
                item(key = "hero", contentType = { "hero" }) {
                    HeroPager(apps = state.featured, onOpen = onOpenAppDetails)
                }
                count += 2
            }

            // --- Favorites ---
            if (favorites.isNotEmpty()) {
                item(key = "favorites-title", contentType = { "section-title" }) {
                    HomeSectionHeader(title = stringResource(UiR.string.home_favorites))
                }
                item(key = "favorites-row", contentType = { "carousel" }) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        items(favorites, key = { "fav:${it.packageName}" }, contentType = { "fav" }) { fav ->
                            ShelfCard(
                                name = fav.name,
                                packageName = fav.packageName,
                                iconUrl = fav.iconUrl,
                                rating = null,
                                iconSize = 64.dp,
                                onClick = { onOpenAppDetails(fav.packageName) },
                            )
                        }
                    }
                }
                count += 2
            }

            if (state.layoutStyle == HomeLayoutStyle.SHELVES) {
                if (catalogEmpty && state.selectedCategory == null) {
                    item(key = "browse-empty", contentType = { "empty" }) {
                        CatalogEmptyState(onAddSource = onAddSource)
                    }
                } else {
                // --- Default layout: one horizontal row per category ---
                val shelfIcon = when (state.iconSize) {
                    IconSize.SMALL -> 64.dp
                    IconSize.MEDIUM -> 80.dp
                    IconSize.LARGE -> 96.dp
                }
                // Play front page, "New in F-Droid", then Play and repository
                // categories interleaved — repository apps are never buried
                // under 19 Play rows.
                val playKeys = state.categories.filter { PlayShelves.isShelf(it) }
                val localKeys = state.categories.filterNot { PlayShelves.isShelf(it) }
                val rowKeys = buildList {
                    add(ROW_PLAY_HOME)
                    if (state.recentlyAdded.isNotEmpty()) add(ROW_FDROID_NEW)
                    val longest = maxOf(playKeys.size, localKeys.size)
                    for (i in 0 until longest) {
                        playKeys.getOrNull(i)?.let(::add)
                        localKeys.getOrNull(i)?.let(::add)
                    }
                }
                // Fixed row height: loading → loaded never shifts the list.
                val rowHeight = shelfIcon + 44.dp
                rowKeys.forEachIndexed { index, key ->
                    item(key = "row-$key", contentType = { "category-row" }) {
                        // Each row observes ONLY its own content: a row that
                        // finishes loading recomposes itself, not Home.
                        val flow = remember(key) { viewModel.rowFlow(key) }
                        val loadedRow by flow.collectAsStateWithLifecycle(initialValue = viewModel.rowSnapshot(key))
                        val apps = if (key == ROW_FDROID_NEW) state.recentlyAdded else loadedRow
                        LaunchedEffect(key) {
                            // This row plus the next two: data is ready
                            // before they scroll into view.
                            listOfNotNull(key, rowKeys.getOrNull(index + 1), rowKeys.getOrNull(index + 2))
                                .filter { it != ROW_FDROID_NEW }
                                .forEach(viewModel::requestRow)
                        }
                        val loaded = apps
                        if (loaded == null || loaded.isNotEmpty()) {
                            Column {
                                HomeSectionHeader(
                                    title = rowLabel(key),
                                    onSeeAll = if (key == ROW_FDROID_NEW) null else ({ onOpenCategory(key) }),
                                )
                                Spacer(Modifier.height(2.dp))
                                if (loaded == null) {
                                    ShelfPlaceholderRow(shelfIcon, rowHeight)
                                } else {
                                    LazyRow(
                                        modifier = Modifier.heightIn(min = rowHeight),
                                        contentPadding = PaddingValues(horizontal = 12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                                    ) {
                                        items(loaded, key = { it.packageName }, contentType = { "shelf-app" }) { app ->
                                            ShelfCard(
                                                name = app.name,
                                                packageName = app.packageName,
                                                iconUrl = app.iconUrl,
                                                rating = app.rating,
                                                iconSize = shelfIcon,
                                                onClick = { onOpenAppDetails(app.packageName) },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                }
            } else {
            // --- Grid / list feed (user-selected via the grid icon) ---
            browseIndex[0] = count
            item(key = "browse-title", contentType = { "section-title" }) {
                HomeSectionHeader(
                    title = state.selectedCategory
                        ?.let { if (PlayShelves.isShelf(it)) PlayShelves.label(it) else NovaCategories.label(it) }
                        ?: stringResource(UiR.string.home_browse_all),
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            item(key = "categories", contentType = { "chips" }) {
                CategoryChipsRow(
                    categories = state.categories,
                    selected = state.selectedCategory,
                    onSelect = viewModel::selectCategory,
                )
            }

            val cellSpacing = if (state.gridColumns >= 4) 8.dp else 12.dp
            if (state.loading && browseApps.isEmpty()) {
                items(3, key = { "browse-shimmer-$it" }, contentType = { "shimmer" }) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(cellSpacing),
                    ) {
                        repeat(state.gridColumns) {
                            ShimmerBox(modifier = Modifier.weight(1f).height(150.dp), cornerRadius = 20.dp)
                        }
                    }
                }
            } else if (browseApps.isEmpty()) {
                item(key = "browse-empty", contentType = { "empty" }) {
                    CatalogEmptyState(onAddSource = onAddSource)
                }
            } else if (state.layoutStyle == HomeLayoutStyle.LIST) {
                items(browseApps, key = { "b:${it.source}:${it.packageName}" }, contentType = { "app" }) { app ->
                    AppCardRow(
                        app = app,
                        onClick = { onOpenAppDetails(app.packageName) },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            } else {
                itemsIndexed(
                    browseRows,
                    key = { index, _ -> "browse-$index" },
                    contentType = { _, _ -> "browse-row" },
                ) { _, row ->
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(cellSpacing),
                    ) {
                        row.forEach { app ->
                            AppGridCell(
                                app = app,
                                onClick = { onOpenAppDetails(app.packageName) },
                                iconSize = state.iconSize.dp.dp,
                                columns = state.gridColumns,
                                spacing = cellSpacing,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        repeat(state.gridColumns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            if (browseCanLoadMore && browseApps.isNotEmpty()) {
                item(key = "browse-more", contentType = { "load-more" }) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        ShimmerBox(modifier = Modifier.size(width = 140.dp, height = 40.dp), cornerRadius = 20.dp)
                    }
                }
            }
            }
        }
        // Search bar overlay: opaque, so the feed passes UNDER it while it
        // is visible, and it slides up under the header when hidden.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { translationY = searchOffset.floatValue }
                .background(MaterialTheme.colorScheme.background)
                .padding(start = 20.dp, end = 20.dp, bottom = 10.dp),
        ) {
            HomeSearchPill(onOpenSearch = onOpenSearch, onScan = startScan)
        }
        }
    }
}

/** Search pill (52dp) + its bottom spacing. */
private val SEARCH_BAR_HEIGHT = 62.dp

/** Index of the favorites row in the list (tiles + optional banners + hero). */
private fun favoritesIndexOf(state: HomeUiState): Int {
    var index = 1
    if (state.offline) index++
    if (state.error != null) index++
    if (state.featured.isNotEmpty()) index += 2 else if (state.loading) index++
    return index
}
