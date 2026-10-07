package com.novastore.app.data.updater

import com.novastore.app.core.model.UpdateCandidate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Decides which source wins when several repositories offer an update for
 * the same package. Deterministic, priority-first:
 * 1) the user's preferred source for the app (handled by the caller, which
 *    short-circuits before this policy runs).
 * 2) lowest repository priority (a random repo can never take over a catalog
 *    a better-placed source manages),
 * 3) highest versionCode,
 * 4) source id.
 * Candidates are already filtered to signatures compatible with the installed
 * app.
 */
@Singleton
class SourceResolutionPolicy @Inject constructor() {

    fun select(
        candidatesBySource: Map<String, UpdateCandidate>,
        priorityOf: (String) -> Int,
    ): UpdateCandidate? =
        candidatesBySource.values
            .filter { it.compatibility.compatible }
            .sortedWith(
                compareBy<UpdateCandidate> { priorityOf(it.source) }
                    .thenByDescending { it.available.versionCode }
                    .thenBy { it.source },
            )
            .firstOrNull()
}
