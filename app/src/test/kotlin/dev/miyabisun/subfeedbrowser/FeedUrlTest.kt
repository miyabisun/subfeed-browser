package dev.miyabisun.subfeedbrowser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FeedUrlTest {
    @Test
    fun keepsAWebAddress() {
        assertEquals("https://feed.example/", feedUrl("https://feed.example/"))
        assertEquals("http://homeserver:8080/list", feedUrl("http://homeserver:8080/list"))
    }

    @Test
    fun trimsWhatAPasteBringsAlong() {
        assertEquals("https://feed.example/", feedUrl("  https://feed.example/\n"))
    }

    @Test
    fun assumesHttpsWhenTheSchemeIsLeftOut() {
        assertEquals("https://feed.example", feedUrl("feed.example"))
        assertEquals("https://feed.example:8443/list", feedUrl("feed.example:8443/list"))
    }

    @Test
    fun refusesWhatCannotBeOpened() {
        for (input in listOf(
            "",
            "   ",
            "https://",
            "https:///list",
            "ftp://feed.example/",
            "javascript:alert(1)",
            "intent://feed.example/#Intent;end",
            "feed example",
            "https://feed.example:port/",
        )) {
            assertNull(input, feedUrl(input))
        }
    }
}
