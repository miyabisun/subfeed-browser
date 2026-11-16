package dev.miyabisun.subfeedbrowser

import org.junit.Assert.assertEquals
import org.junit.Test

class BackActionTest {
    @Test
    fun leavesFullScreenBeforeAnythingElse() {
        assertEquals(BackAction.EXIT_FULL_SCREEN, backAction(Screen.VIDEO, fullScreen = true, canGoBack = true))
        assertEquals(
            BackAction.EXIT_FULL_SCREEN,
            backAction(Screen.VIDEO, fullScreen = true, canGoBack = false),
        )
        assertEquals(BackAction.EXIT_FULL_SCREEN, backAction(Screen.FEED, fullScreen = true, canGoBack = false))
    }

    @Test
    fun walksBackThroughTheVideoScreenThenReturnsToTheFeed() {
        assertEquals(BackAction.GO_BACK, backAction(Screen.VIDEO, fullScreen = false, canGoBack = true))
        assertEquals(BackAction.SHOW_FEED, backAction(Screen.VIDEO, fullScreen = false, canGoBack = false))
    }

    @Test
    fun walksBackThroughTheFeedThenClosesTheApp() {
        assertEquals(BackAction.GO_BACK, backAction(Screen.FEED, fullScreen = false, canGoBack = true))
        assertEquals(BackAction.CLOSE_APP, backAction(Screen.FEED, fullScreen = false, canGoBack = false))
    }

    @Test
    fun walksBackThroughAWindowOfTheAppThenClosesIt() {
        assertEquals(BackAction.GO_BACK, backAction(Screen.POPUP, fullScreen = false, canGoBack = true))
        assertEquals(BackAction.CLOSE_POPUP, backAction(Screen.POPUP, fullScreen = false, canGoBack = false))
    }
}
