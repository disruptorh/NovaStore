package com.novastore.app.data.repository

import com.novastore.app.core.model.UpdateState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateRetentionTest {

    private val now = 1_000_000L
    private val kept = setOf("com.example.kept")

    @Test
    fun keptPackageIsNeverDeleted() {
        assertFalse(shouldDeleteUpdateRow("com.example.kept", kept, UpdateState.DISCOVERED.name, updatedAt = now, now = now))
    }

    @Test
    fun missingPackageWithTerminalStateIsDeleted() {
        assertTrue(shouldDeleteUpdateRow("com.example.gone", kept, UpdateState.FAILED.name, updatedAt = now, now = now))
    }

    @Test
    fun freshInFlightRowSurvivesScanCleanup() {
        // QUEUED/DOWNLOADING/VERIFYING/INSTALLING, updated moments ago: an
        // active download must never be deleted under the user.
        for (state in listOf(
            UpdateState.QUEUED,
            UpdateState.DOWNLOADING,
            UpdateState.VERIFYING,
            UpdateState.INSTALLING,
        )) {
            assertFalse(
                "$state fresh in-flight must be kept",
                shouldDeleteUpdateRow(
                    "com.example.busy",
                    kept,
                    state.name,
                    updatedAt = now - 1_000L,
                    now = now,
                ),
            )
        }
    }

    @Test
    fun staleInFlightZombieIsDeleted() {
        val stale = now - IN_FLIGHT_STALENESS_MS - 1
        assertTrue(
            "a DOWNLOADING row untouched for over a day is a dead task",
            shouldDeleteUpdateRow("com.example.zombie", kept, UpdateState.DOWNLOADING.name, updatedAt = stale, now = now),
        )
    }
}