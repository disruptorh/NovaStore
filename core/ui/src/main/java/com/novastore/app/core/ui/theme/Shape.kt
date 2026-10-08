package com.novastore.app.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * Nova Store corner radii. Only three radii exist in the system:
 * 8dp chips, 12dp cards, 28dp sheets/modals.
 */
object NovaShapes {
    /** 8dp — chips, pills, small interactive elements. */
    val Chip = RoundedCornerShape(8.dp)

    /** 12dp — cards, list rows, buttons. */
    val Card = RoundedCornerShape(12.dp)

    /** 28dp — sheets, hero banners and large surfaces. */
    val Sheet = RoundedCornerShape(28.dp)
}

/**
 * Material 3 component-slot shapes derived from [NovaShapes]. Feeds
 * `MaterialTheme(shapes = ...)` so M3 components inherit the Nova radii.
 */
val NovaMaterialShapes: androidx.compose.material3.Shapes = androidx.compose.material3.Shapes(
    extraSmall = NovaShapes.Chip,
    small = NovaShapes.Card,
    medium = NovaShapes.Card,
    large = NovaShapes.Card,
    extraLarge = NovaShapes.Sheet,
)