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
        assertEquals(96, keptAbove(bottom = 96, height = 2000, fullScreen = false))
        assertEquals("up to a fifth of the screen", 400, keptAbove(bottom = 400, height = 2000, fullScreen = false))
    }

    @Test
    fun givesYouTubeTheWholeScreenWhenNothingIsKept() {
        assertEquals(0, keptAbove(bottom = 0, height = 2000, fullScreen = false))
    }

    @Test
    fun givesAFullScreenVideoTheWholeScreen() {
        assertEquals(0, keptAbove(bottom = 96, height = 2000, fullScreen = true))
    }

    @Test
    fun givesYouTubeTheWholeScreenRatherThanTooLittleOfIt() {
        assertEquals(0, keptAbove(bottom = 401, height = 2000, fullScreen = false))
        assertEquals(
            "a keyboard over a screen on its side leaves a strip",
            0,
            keptAbove(bottom = 111, height = 250, fullScreen = false),
        )
        assertEquals("before the screen is laid out", 0, keptAbove(bottom = 96, height = 0, fullScreen = false))
        assertEquals(
            "a list that claims more than any screen has",
            0,
            keptAbove(bottom = Int.MAX_VALUE, height = 2000, fullScreen = false),
        )
    }

    @Test
    fun closesYouTubeWhenTheKeptPartIsPressed() {
        assertTrue(closesVideo("keep-pressed", Screen.VIDEO, fullScreen = false))
    }

    @Test
    fun leavesTheScreenAloneOtherwise() {
        assertFalse("the list is in front already", closesVideo("keep-pressed", Screen.FEED, fullScreen = false))
        assertFalse(
            "nothing of the list shows around a full-screen video",
            closesVideo("keep-pressed", Screen.VIDEO, fullScreen = true),
        )
        assertFalse(closesVideo("keep", Screen.VIDEO, fullScreen = false))
        assertFalse(closesVideo("open-video", Screen.VIDEO, fullScreen = false))
        assertFalse(closesVideo(null, Screen.VIDEO, fullScreen = false))
    }
}
