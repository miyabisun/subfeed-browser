package dev.miyabisun.subfeedbrowser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mozilla.geckoview.PanZoomController.INPUT_RESULT_HANDLED
import org.mozilla.geckoview.PanZoomController.INPUT_RESULT_HANDLED_CONTENT
import org.mozilla.geckoview.PanZoomController.INPUT_RESULT_IGNORED
import org.mozilla.geckoview.PanZoomController.INPUT_RESULT_UNHANDLED
import org.mozilla.geckoview.PanZoomController.OVERSCROLL_FLAG_HORIZONTAL
import org.mozilla.geckoview.PanZoomController.OVERSCROLL_FLAG_NONE
import org.mozilla.geckoview.PanZoomController.OVERSCROLL_FLAG_VERTICAL
import org.mozilla.geckoview.PanZoomController.SCROLLABLE_FLAG_BOTTOM
import org.mozilla.geckoview.PanZoomController.SCROLLABLE_FLAG_LEFT
import org.mozilla.geckoview.PanZoomController.SCROLLABLE_FLAG_NONE
import org.mozilla.geckoview.PanZoomController.SCROLLABLE_FLAG_RIGHT
import org.mozilla.geckoview.PanZoomController.SCROLLABLE_FLAG_TOP

class PullTest {
    private val video = "https://m.youtube.com/watch?v=aqz-KE-bpKQ"

    @Test
    fun readsAYouTubePageAgain() {
        assertTrue(reloadsOnPull(Screen.VIDEO, fullScreen = false, loading = false, url = video))
        assertTrue(reloadsOnPull(Screen.VIDEO, false, false, "https://www.youtube.com/shorts/aqz-KE-bpKQ"))
        assertTrue(reloadsOnPull(Screen.VIDEO, false, false, "https://m.youtube.com/@blender"))
    }

    @Test
    fun leavesTheListToItsOwnPage() {
        assertFalse(reloadsOnPull(Screen.FEED, false, false, "https://feed.example/"))
        assertFalse("whatever the list shows", reloadsOnPull(Screen.FEED, false, false, video))
    }

    @Test
    fun leavesAFullScreenVideoAlone() {
        assertFalse(reloadsOnPull(Screen.VIDEO, fullScreen = true, loading = false, url = video))
    }

    @Test
    fun doesNotReadAPageAgainWhileItLoads() {
        assertFalse(reloadsOnPull(Screen.VIDEO, fullScreen = false, loading = true, url = video))
    }

    @Test
    fun leavesPagesThatAreNotYouTubeAlone() {
        for (url in listOf(
            "https://accounts.google.com/v3/signin/identifier?continue=https%3A%2F%2Fm.youtube.com%2F",
            "https://accounts.google.co.jp/accounts/SetSID",
            "https://www.google.com/",
            "https://youtube.com.example/watch?v=aqz-KE-bpKQ",
            "https://accounts.google.com@m.youtube.com.example/",
            "about:blank",
            "intent://www.youtube.com/watch?v=aqz-KE-bpKQ",
        )) {
            assertFalse(url, reloadsOnPull(Screen.VIDEO, false, false, url))
        }
        assertFalse("before the page has an address", reloadsOnPull(Screen.VIDEO, false, false, null))
    }

    @Test
    fun pullsAPageThatIsAtItsTop() {
        assertTrue(
            "the page scrolls, and only further down",
            pullsFromTheTop(INPUT_RESULT_HANDLED, SCROLLABLE_FLAG_BOTTOM, OVERSCROLL_FLAG_VERTICAL),
        )
        assertTrue(
            "a page too short to scroll",
            pullsFromTheTop(INPUT_RESULT_UNHANDLED, SCROLLABLE_FLAG_NONE, OVERSCROLL_FLAG_VERTICAL),
        )
        assertTrue(
            "sideways scrolling is no way up",
            pullsFromTheTop(
                INPUT_RESULT_HANDLED,
                SCROLLABLE_FLAG_BOTTOM or SCROLLABLE_FLAG_LEFT or SCROLLABLE_FLAG_RIGHT,
                OVERSCROLL_FLAG_VERTICAL or OVERSCROLL_FLAG_HORIZONTAL,
            ),
        )
    }

    @Test
    fun scrollsAPageThatWasReadFurtherDown() {
        assertFalse(
            pullsFromTheTop(
                INPUT_RESULT_HANDLED,
                SCROLLABLE_FLAG_TOP or SCROLLABLE_FLAG_BOTTOM,
                OVERSCROLL_FLAG_VERTICAL,
            ),
        )
        assertFalse(
            "at its very end",
            pullsFromTheTop(INPUT_RESULT_HANDLED, SCROLLABLE_FLAG_TOP, OVERSCROLL_FLAG_VERTICAL),
        )
    }

    @Test
    fun leavesATouchThePageUsesToThePage() {
        assertFalse(
            "a player, a seek bar, a list inside the page",
            pullsFromTheTop(INPUT_RESULT_HANDLED_CONTENT, SCROLLABLE_FLAG_BOTTOM, OVERSCROLL_FLAG_VERTICAL),
        )
        assertFalse(
            pullsFromTheTop(INPUT_RESULT_HANDLED_CONTENT, SCROLLABLE_FLAG_NONE, OVERSCROLL_FLAG_VERTICAL),
        )
    }

    @Test
    fun leavesAPageThatForbidsPullingPastItsTop() {
        assertFalse(pullsFromTheTop(INPUT_RESULT_HANDLED, SCROLLABLE_FLAG_BOTTOM, OVERSCROLL_FLAG_NONE))
        assertFalse(pullsFromTheTop(INPUT_RESULT_HANDLED, SCROLLABLE_FLAG_BOTTOM, OVERSCROLL_FLAG_HORIZONTAL))
        assertFalse(pullsFromTheTop(INPUT_RESULT_UNHANDLED, SCROLLABLE_FLAG_NONE, OVERSCROLL_FLAG_NONE))
    }

    @Test
    fun doesNotPullOnAnAnswerItCannotRead() {
        assertFalse(pullsFromTheTop(INPUT_RESULT_IGNORED, SCROLLABLE_FLAG_NONE, OVERSCROLL_FLAG_VERTICAL))
        assertFalse(pullsFromTheTop(7, SCROLLABLE_FLAG_NONE, OVERSCROLL_FLAG_VERTICAL))
    }

    @Test
    fun takesOneFingerMovingDownForAPull() {
        assertTrue(movesLikeAPull(fingers = 1, across = 0f, down = 30f))
        assertTrue("a finger never moves quite straight", movesLikeAPull(fingers = 1, across = -12f, down = 30f))
    }

    @Test
    fun leavesOtherMovesToThePage() {
        assertFalse("sideways, as along a seek bar", movesLikeAPull(fingers = 1, across = 40f, down = 12f))
        assertFalse(movesLikeAPull(fingers = 1, across = -40f, down = 12f))
        assertFalse("as much sideways as down", movesLikeAPull(fingers = 1, across = 30f, down = 30f))
        assertFalse("upward", movesLikeAPull(fingers = 1, across = 0f, down = -30f))
        assertFalse("not moving", movesLikeAPull(fingers = 1, across = 0f, down = 0f))
        assertFalse("two fingers zoom", movesLikeAPull(fingers = 2, across = 0f, down = 30f))
    }
}
