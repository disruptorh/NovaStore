package com.novastore.app.core.model

/** Outcome of a source backup import (P06-T05). */
data class ImportReport(
    val added: Int,
    val updated: Int,
    val rejected: Int,
)