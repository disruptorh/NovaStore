package com.novastore.app.core.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Nova Store elevations. Surfaces are outlined rather than stacked: cards sit
 * at 0dp (drawn with outline/shape), the search bar lifts 2dp and sheets 6dp.
 */
object NovaElevation {
    /** 0dp — cards (visibility comes from shape/outline). */
    val CARDS = 0.dp

    /** 2dp — search bar and floating controls. */
    val SEARCH_BAR = 2.dp

    /** 6dp — bottom sheets and modals. */
    val SHEET = 6.dp
}