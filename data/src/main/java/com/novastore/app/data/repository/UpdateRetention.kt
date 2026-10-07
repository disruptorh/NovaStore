package com.novastore.app.data.repository

import com.novastore.app.core.model.UpdateState

/** States whose row is an active download/install task, not merely a listing. */
internal val IN_FLIGHT_STATES: Set<String> = setOf(
    UpdateState.QUEUED.name,
    UpdateState.DOWNLOADING.name,
    UpdateState.VERIFYING.name,
    UpdateState.INSTALLING.name,
)

/** Any in-flight row untouched for this long is a zombie, not a download. */
internal const val IN_FLIGHT_STALENESS_MS = 24L * 60 * 60 * 1000

/**
 * Scan-cleanup decision for one stored update row
 * ([UpdatesRepositoryImpl.retainOnly]): a row whose package no longer appears
 * among the scan results is deleted — EXCEPT a row still actively
 * downloading/installing while fresh. A running task is never removed under
 * the user; only expired zombies (killed process / lost task) are.
 */
internal fun shouldDeleteUpdateRow(
    rowPackage: String,
    keptPackages: Set<String>,
    state: String,
    updatedAt: Long,
    now: Long,
): Boolean {
    val inFlight = state in IN_FLIGHT_STATES
    val staleInFlight = inFlight && now - updatedAt > IN_FLIGHT_STALENESS_MS
    return rowPackage !in keptPackages && (!inFlight || staleInFlight)
}