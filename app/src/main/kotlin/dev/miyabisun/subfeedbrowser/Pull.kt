package dev.miyabisun.subfeedbrowser

import org.mozilla.geckoview.PanZoomController

/**
 * Whether pulling this page down from its top reads it again. Only a YouTube page that is not
 * loading does: the list has a pull of its own, and a sign-in page would lose what was typed.
 */
fun reloadsOnPull(
    screen: Screen,
    fullScreen: Boolean,
    loading: Boolean,
    url: String?,
): Boolean = screen == Screen.VIDEO && !fullScreen && !loading && url?.let(WebAddress::parse)?.isYouTube == true

/**
 * Whether a touch may pull the page down past its top, from what the engine answered about it
 * (`PanZoomController.InputResultDetail`). It may when the page neither uses the touch itself
 * nor has anything left to scroll to above, and allows being pulled past its edge.
 */
fun pullsFromTheTop(
    handled: Int,
    scrollable: Int,
    overscroll: Int,
): Boolean {
    val leftToTheApp =
        handled == PanZoomController.INPUT_RESULT_UNHANDLED || handled == PanZoomController.INPUT_RESULT_HANDLED
    val scrollsUp = scrollable and PanZoomController.SCROLLABLE_FLAG_TOP != 0
    val pullsPastItsEdge = overscroll and PanZoomController.OVERSCROLL_FLAG_VERTICAL != 0
    return leftToTheApp && !scrollsUp && pullsPastItsEdge
}

/** Whether a move since the last one is a pull: one finger, going down more than sideways. */
fun movesLikeAPull(
    fingers: Int,
    across: Float,
    down: Float,
): Boolean = fingers == 1 && down > 0 && down > kotlin.math.abs(across)
