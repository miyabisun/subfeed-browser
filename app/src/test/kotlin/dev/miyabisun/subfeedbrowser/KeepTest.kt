package dev.miyabisun.subfeedbrowser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KeepTest {
    @Test
    fun readsTheBottomEdgeAListReports() {
        assertEquals(96, keepBottom("keep", 96))
        assertEquals("a page measures in fractions", 97, keepBottom("keep", 96.5))
        assertEquals("a list that keeps nothing in view", 0, keepBottom("keep", 0))
        assertEquals("a header scrolled out of view keeps nothing", 0, keepBottom("keep", -40))
    }

    @Test
    fun ignoresMessagesThatAreNoSuchReport() {
        assertNull(keepBottom("open-video", 96))
        assertNull(keepBottom(null, 96))
        assertNull(keepBottom("keep", "96"))
        assertNull(keepBottom("keep", null))
        assertNull(keepBottom("keep", Double.NaN))
        assertNull(keepBottom("keep", Double.POSITIVE_INFINITY))
    }

    @Test
    fun keepsTheListDownToTheReportedEdge() {
        assertEquals(96, keptAbove(bottom = 96, over = 96, height = 2000, immersive = false))
        assertEquals(
            "up to a fifth of the screen",
            400,
            keptAbove(bottom = 400, over = 400, height = 2000, immersive = false),
        )
    }

    @Test
    fun givesYouTubeTheWholeScreenWhenNothingIsKept() {
        assertEquals(0, keptAbove(bottom = 0, over = 0, height = 2000, immersive = false))
    }

    @Test
    fun givesAVideoThatFillsTheScreenTheWholeScreen() {
        assertEquals(0, keptAbove(bottom = 96, over = 96, height = 2000, immersive = true))
        assertEquals(
            "a menu left open behind it too",
            0,
            keptAbove(bottom = 96, over = 700, height = 2000, immersive = true),
        )
    }

    @Test
    fun givesYouTubeTheWholeScreenRatherThanTooLittleOfIt() {
        assertEquals(0, keptAbove(bottom = 401, over = 401, height = 2000, immersive = false))
        assertEquals(
            "a keyboard over a screen on its side leaves a strip",
            0,
            keptAbove(bottom = 111, over = 111, height = 250, immersive = false),
        )
        assertEquals(
            "before the screen is laid out",
            0,
            keptAbove(bottom = 96, over = 96, height = 0, immersive = false),
        )
        assertEquals(
            "a list that claims more than any screen has",
            0,
            keptAbove(bottom = Int.MAX_VALUE, over = Int.MAX_VALUE, height = 2000, immersive = false),
        )
    }

    @Test
    fun makesRoomForWhatTheKeptPartOpens() {
        assertEquals(
            "a menu under the header",
            760,
            keptAbove(bottom = 96, over = 760, height = 2000, immersive = false),
        )
        assertEquals(
            "more than a fifth: it is open only for a moment",
            1500,
            keptAbove(bottom = 96, over = 1500, height = 2000, immersive = false),
        )
        assertEquals(
            "no further than the screen",
            2000,
            keptAbove(bottom = 96, over = 2600, height = 2000, immersive = false),
        )
        assertEquals(
            "a report from an older page that names no such edge",
            96,
            keptAbove(bottom = 96, over = 0, height = 2000, immersive = false),
        )
    }

    @Test
    fun opensNothingOutOfAHeaderThatIsNotKept() {
        assertEquals(0, keptAbove(bottom = 401, over = 900, height = 2000, immersive = false))
        assertEquals(0, keptAbove(bottom = 0, over = 900, height = 2000, immersive = false))
    }

    @Test
    fun showsTheListWhenItMovesToAnotherPage() {
        assertTrue(listMoved(Screen.VIDEO, "https://feed.example/", "https://feed.example/group/3"))
        assertTrue(listMoved(Screen.VIDEO, "https://feed.example/group/3", "https://feed.example/channels"))
    }

    @Test
    fun leavesTheVideoInFrontOtherwise() {
        assertFalse(
            "the list is in front already",
            listMoved(Screen.FEED, "https://feed.example/", "https://feed.example/group/3"),
        )
        assertFalse("the same page", listMoved(Screen.VIDEO, "https://feed.example/", "https://feed.example/"))
        assertFalse("its first page", listMoved(Screen.VIDEO, null, "https://feed.example/"))
        assertFalse(listMoved(Screen.VIDEO, "https://feed.example/", null))
    }
}
