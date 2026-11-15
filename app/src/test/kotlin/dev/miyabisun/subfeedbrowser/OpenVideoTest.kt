package dev.miyabisun.subfeedbrowser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OpenVideoTest {
    @Test
    fun acceptsYouTubeWatchAndShorts() {
        for (url in listOf(
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            "https://www.youtube.com/shorts/dQw4w9WgXcQ",
            "https://m.youtube.com/watch?v=dQw4w9WgXcQ",
            "https://youtu.be/dQw4w9WgXcQ",
            "http://www.youtube.com/watch?v=dQw4w9WgXcQ",
        )) {
            assertEquals(url, openVideoUrl("open-video", url))
        }
    }

    @Test
    fun ignoresOtherSites() {
        for (url in listOf(
            "https://evil.example/watch?v=dQw4w9WgXcQ",
            "https://accounts.google.com/",
            "https://youtube.com.evil.example/watch?v=x",
            "vnd.youtube:dQw4w9WgXcQ",
            "intent://www.youtube.com/watch?v=x#Intent;end",
            "",
        )) {
            assertNull(url, openVideoUrl("open-video", url))
        }
    }

    @Test
    fun ignoresMessagesOfAnotherShape() {
        val url = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
        assertNull(openVideoUrl("mark-watched", url))
        assertNull(openVideoUrl(null, url))
        assertNull(openVideoUrl("open-video", null))
        assertNull(openVideoUrl("open-video", 42))
        assertNull(openVideoUrl("open-video", listOf(url)))
    }
}
