package dev.miyabisun.subfeedbrowser

enum class BackAction { EXIT_FULL_SCREEN, GO_BACK, CLOSE_POPUP, SHOW_FEED, CLOSE_APP }

/** What the back gesture does. `canGoBack` is about the history of the screen in front. */
fun backAction(
    screen: Screen,
    fullScreen: Boolean,
    canGoBack: Boolean,
): BackAction =
    when {
        fullScreen -> BackAction.EXIT_FULL_SCREEN
        canGoBack -> BackAction.GO_BACK
        screen == Screen.POPUP -> BackAction.CLOSE_POPUP
        screen == Screen.VIDEO -> BackAction.SHOW_FEED
        else -> BackAction.CLOSE_APP
    }
