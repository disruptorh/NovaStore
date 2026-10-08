package com.novastore.app.core.ui.theme

/**
 * Nova Store motion durations (milliseconds, Material 3 standard/emphasized
 * scale). Animated transitions reference these tokens; reduced-motion
 * preferences are honoured via the accessibility MotionDurationScale.
 */
object NovaMotion {
    /** 120ms — micro-interactions and chip/list-item state changes. */
    const val QUICK = 120

    /** 200ms — standard visibility and layout transitions. */
    const val MIDDLE = 200

    /** 400ms — emphasized hero, sheet and page transitions. */
    const val EMPHASIZED = 400
}