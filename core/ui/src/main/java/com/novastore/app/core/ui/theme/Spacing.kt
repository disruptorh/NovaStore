package com.novastore.app.core.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Nova Store spacing scale. The only spacing values used in the UI:
 * 4, 8, 12, 16, 24, 32 and 48 dp. Layout code must reference these tokens
 * instead of writing bare dp numbers (checked by the design-system audit).
 */
object NovaSpacing {
    /** 4dp — inline gaps within chips and tight icon rows. */
    val XS = 4.dp

    /** 8dp — compact gaps inside rows/cards. */
    val SM = 8.dp

    /** 12dp — default gap between adjacent elements. */
    val MD = 12.dp

    /** 16dp — outer screen padding and insets. */
    val LG = 16.dp

    /** 24dp — section separation and dialogs. */
    val XL = 24.dp

    /** 32dp — screen edge insets for heroes and sheets. */
    val XXL = 32.dp

    /** 48dp — large separators and button height. */
    val XXXL = 48.dp
}