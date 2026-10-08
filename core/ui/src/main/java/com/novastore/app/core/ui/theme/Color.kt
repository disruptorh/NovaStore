package com.novastore.app.core.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Nova Store brand palette.
 *
 * Dark-first premium Material 3: deep charcoal backgrounds with an emerald
 * accent. Amber is reserved for secondary highlights, violet only for charts.
 * The user-selectable accent palettes live in [AccentPalettes].
 *
 * Contrast policy (WCAG AA): every accent palette ships its own on-primary
 * colors for light and dark mode ([NovaAccent.onPrimaryLight],
 * [NovaAccent.onPrimaryDark]) chosen to keep text on primary ≥ 4.5:1.
 * White-on-gradient (hero cards, NovaGradientButton) is treated as a
 * decorative/large-type pattern and documented as such — plain text on
 * accent-saturated gradients is never used for body copy. Semantic colors
 * (error/ok/warning/offline) never reuse the accent so an error is never
 * ambiguously "brand-coloured".
 */

// Brand emerald — primary accent.
val EmeraldLight = Color(0xFF00C383) // primary in light mode
val EmeraldDark = Color(0xFF00B377) // primary in dark mode
val EmeraldContainerDark = Color(0xFF06392A)
val OnEmeraldContainerDark = Color(0xFF7BFFCF)
val EmeraldContainerLight = Color(0xFFA5F5D6)
val OnEmeraldContainerLight = Color(0xFF002B1E)

/** Text/icons drawn on top of the accent gradient — bright, not dark. */
val OnEmerald = Color(0xFFFFFFFF)

// Secondary amber.
val Amber = Color(0xFFFFB454)
val OnAmber = Color(0xFF332004)
val AmberContainerDark = Color(0xFF42300B)
val OnAmberContainerDark = Color(0xFFFFD9A0)
val AmberContainerLight = Color(0xFFFFDDB3)
val OnAmberContainerLight = Color(0xFF2B2000)

// Tertiary violet (charts / data viz only).
val Violet = Color(0xFFC9B8FF)

// Dark surfaces.
val Night0 = Color(0xFF0E1013) // background
val Night1 = Color(0xFF16191E) // surfaceContainer
val Night2 = Color(0xFF1D2127) // surfaceContainerHigh
val Night3 = Color(0xFF23272E) // surfaceContainerHighest
val NightVariant = Color(0xFF262B33)
val OnNight = Color(0xFFE4E7EB)
val OnNightVariant = Color(0xFF9BA6AF)
val NightOutline = Color(0xFF454C55)
val NightOutlineVariant = Color(0xFF2A2F36)

// Light surfaces.
val Paper = Color(0xFFF7FAF8)
val PaperContainerLowest = Color(0xFFFFFFFF)
val PaperContainerLow = Color(0xFFF1F5F2)
val PaperContainer = Color(0xFFEBEFEA)
val PaperContainerHigh = Color(0xFFE5EAE4)
val PaperContainerHighest = Color(0xFFDFE5DE)
val PaperVariant = Color(0xFFDFE5E1)
val OnPaper = Color(0xFF16191E)
val OnPaperVariant = Color(0xFF414947)
val PaperOutline = Color(0xFF71787A)
val PaperOutlineVariant = Color(0xFFC1C9C6)

// AMOLED surfaces (pure black theme).
val Amoled0 = Color(0xFF000000)
val Amoled1 = Color(0xFF0C0E10)
val Amoled2 = Color(0xFF141719)
val Amoled3 = Color(0xFF1B1E21)
val AmoledVariant = Color(0xFF202427)
val AmoledOutline = Color(0xFF3D444C)
val AmoledOutlineVariant = Color(0xFF24282C)

/**
 * A selectable accent palette: everything the theme and the gradients need.
 * On-primary pairs honour WCAG AA (text on primary ≥ 4.5:1).
 */
data class NovaAccent(
    /** Gradient start (the "primary" brand color). */
    val start: Color,
    /** Gradient end. */
    val end: Color,
    /** Material primary in light mode. */
    val primaryLight: Color,
    /** Material primary in dark mode. */
    val primaryDark: Color,
    val containerLight: Color,
    val onContainerLight: Color,
    val containerDark: Color,
    val onContainerDark: Color,
    /** Text on primary in light mode (≥ 4.5:1 vs primaryLight). */
    val onPrimaryLight: Color,
    /** Text on primary in dark mode (≥ 4.5:1 vs primaryDark). */
    val onPrimaryDark: Color,
)

/** Gradient + Material colors for every [com.novastore.app.core.model.AccentPalette]. */
object AccentPalettes {
    val NOVA = NovaAccent(
        start = Color(0xFF6965F1),
        end = Color(0xFFA556F7),
        primaryLight = Color(0xFF5B57E8),
        primaryDark = Color(0xFFB3B0FF),
        containerLight = Color(0xFFE3E1FF),
        onContainerLight = Color(0xFF1B1664),
        containerDark = Color(0xFF34307A),
        onContainerDark = Color(0xFFE3E1FF),
        onPrimaryLight = OnEmerald,
        onPrimaryDark = Color(0xFF1B1664),
    )
    val OCEAN = NovaAccent(
        start = Color(0xFF0E9CB0),
        end = Color(0xFF0A7E96),
        primaryLight = Color(0xFF0E97AC),
        primaryDark = Color(0xFF2FB8CB),
        containerLight = Color(0xFFC8F0F6),
        onContainerLight = Color(0xFF03303A),
        containerDark = Color(0xFF073B45),
        onContainerDark = Color(0xFFA9EBF5),
        onPrimaryLight = Color(0xFF00262E),
        onPrimaryDark = Color(0xFF00262E),
    )
    val VIOLET = NovaAccent(
        start = Color(0xFF8B5CF6),
        end = Color(0xFF6D28D9),
        primaryLight = Color(0xFF7C4DF0),
        primaryDark = Color(0xFFB39DFF),
        containerLight = Color(0xFFE9DDFF),
        onContainerLight = Color(0xFF271266),
        containerDark = Color(0xFF392B69),
        onContainerDark = Color(0xFFE4DBFF),
        onPrimaryLight = OnEmerald,
        onPrimaryDark = Color(0xFF271266),
    )
    val AMBER = NovaAccent(
        start = Color(0xFFD97706),
        end = Color(0xFFB45309),
        primaryLight = Color(0xFFB45309),
        primaryDark = Color(0xFFF5B04C),
        containerLight = Color(0xFFFFE3B8),
        onContainerLight = Color(0xFF3B2404),
        containerDark = Color(0xFF453009),
        onContainerDark = Color(0xFFFFDCA8),
        onPrimaryLight = OnEmerald,
        onPrimaryDark = Color(0xFF3B2404),
    )
    val GRAPHITE = NovaAccent(
        start = Color(0xFF64748B),
        end = Color(0xFF334155),
        primaryLight = Color(0xFF475569),
        primaryDark = Color(0xFFB8C4D4),
        containerLight = Color(0xFFE2E8F0),
        onContainerLight = Color(0xFF1E293B),
        containerDark = Color(0xFF2B3544),
        onContainerDark = Color(0xFFE2E8F0),
        onPrimaryLight = OnEmerald,
        onPrimaryDark = Color(0xFF1E293B),
    )

    /**
     * [com.novastore.app.core.model.AccentPalette.DYNAMIC] reuses the brand
     * NOVA palette as its fallback (used when dynamic color is unavailable,
     * e.g. pre-S devices or the gradient swatches in Settings).
     */
    fun of(palette: com.novastore.app.core.model.AccentPalette): NovaAccent = when (palette) {
        com.novastore.app.core.model.AccentPalette.NOVA -> NOVA
        com.novastore.app.core.model.AccentPalette.OCEAN -> OCEAN
        com.novastore.app.core.model.AccentPalette.VIOLET -> VIOLET
        com.novastore.app.core.model.AccentPalette.AMBER -> AMBER
        com.novastore.app.core.model.AccentPalette.GRAPHITE -> GRAPHITE
        com.novastore.app.core.model.AccentPalette.DYNAMIC -> NOVA
    }
}

// ----------------------------------------------------------------------
// Brand decorative gradients
//
// Decorative-only brand accents (wordmark, quick tiles, hero cards). They
// carry no semantic meaning, are never used for body text on a colored
// background without a contrast check, and are shared by feature modules
// so no screen re-declares its own copy of the brand hex values.
// ----------------------------------------------------------------------

/** Brand indigo — decorative gradient start (wordmark, tiles, hero dots). */
val BrandIndigo = Color(0xFF6965F1)

/** Brand violet — decorative gradient end, paired with [BrandIndigo]. */
val BrandViolet = Color(0xFFA556F7)

/**
 * Rating gold — filled star fills and review accents. A lighter gold than
 * [Amber] so star rows stay readable on both the compact cards and the
 * saturated hero gradient in Details.
 */
val StarAmber = Color(0xFFFFE082)

/** Filled-favorite heart accent (use a neutral tint for the outline state). */
val Favorite = Color(0xFFE5486B)

/** Brand gradient of the "Nova Store" wordmark: indigo → violet. */
val NovaTitleBrush: Brush = Brush.linearGradient(colors = listOf(BrandIndigo, BrandViolet))

/** "Everything is up to date" — calm green instead of the call-to-action gradient. */
val UpToDateBrush: Brush = Brush.linearGradient(colors = listOf(Color(0xFF12B886), Color(0xFF0CA678), Color(0xFF15AABF)))

/** Decorative brand gradients for hero/shelf cards, cycled per page. */
val HeroGradients: List<List<Color>> = listOf(
    listOf(Color(0xFF6965F1), Color(0xFFA556F7)),
    listOf(Color(0xFF3B5BDB), Color(0xFF6965F1)),
    listOf(Color(0xFFA556F7), Color(0xFFE64980)),
    listOf(Color(0xFF0CA678), Color(0xFF3B5BDB)),
    listOf(Color(0xFFF76707), Color(0xFFE64980)),
    listOf(Color(0xFF1C7ED6), Color(0xFF15AABF)),
)
