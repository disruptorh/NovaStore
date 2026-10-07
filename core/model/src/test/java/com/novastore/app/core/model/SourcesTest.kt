package com.novastore.app.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SourcesTest {

    @Test
    fun `play web is a distinct metadata-only source`() {
        assertNotEquals(SOURCE_PLAY, SOURCE_PLAY_WEB)
        assertEquals("play", SOURCE_PLAY)
        assertEquals("play-web", SOURCE_PLAY_WEB)
    }

    @Test
    fun `no mirror sources exist anymore`() {
        // Mirrors were fully removed: the only sources left are play,
        // play-web, fdroid and the repository/installed ids.
        assertEquals(
            listOf(SOURCE_PLAY, SOURCE_PLAY_WEB, SOURCE_FDROID, SOURCE_GITHUB, SOURCE_GITLAB),
            listOf("play", "play-web", "fdroid", "github", "gitlab"),
        )
    }
}