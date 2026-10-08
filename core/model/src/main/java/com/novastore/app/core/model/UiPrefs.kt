package com.novastore.app.core.model

/**
 * UI preferences shared between the settings screens, the theme and the
 * persistence layer. All values are stored as their [name] in Preferences
 * DataStore.
 */

/** Application theme mode. */
enum class ThemeMode {
    /** Follow the system light/dark setting. */
    SYSTEM,

    /** Always light. */
    LIGHT,

    /** Always dark (deep charcoal). */
    DARK,

    /** Pure black dark mode for AMOLED panels. */
    AMOLED,
}

/**
 * Brand accent palette used for gradients, primary color and highlights.
 * Reduced to the Nova signature accent plus three companions and the system
 * dynamic option; every design must fit one of these.
 */
enum class AccentPalette {
    /** Default Nova brand accent (indigo → violet). */
    NOVA,

    /** Deep cyan / petrol. */
    OCEAN,

    /** Purple. */
    VIOLET,

    /** Warm amber. */
    AMBER,

    /** Neutral slate. */
    GRAPHITE,

    /** Android 12+ dynamic colors from the wallpaper (NOVA fallback below S). */
    DYNAMIC,
}

/** In-app language override (applied via per-app locales). */
enum class AppLanguage(val tag: String?, val nativeName: String) {
    SYSTEM(null, "System"),
    ENGLISH("en", "English"),
    RUSSIAN("ru", "Русский"),
    FRENCH("fr", "Français"),
    SPANISH("es", "Español"),
}

/** Icon size inside Home grid cells. */
enum class IconSize(val dp: Int) {
    SMALL(48),
    MEDIUM(64),
    LARGE(84),
}

/** How the "browse" catalog is presented on Home. */
enum class HomeLayoutStyle {
    /** Multi-column grid of icon+label cells. */
    GRID,

    /** Single-column list rows with rich metadata. */
    LIST,

    /** Default: horizontal rows per category with "See all". */
    SHELVES,
}
