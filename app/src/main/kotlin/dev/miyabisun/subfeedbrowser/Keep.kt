package dev.miyabisun.subfeedbrowser

import kotlin.math.roundToInt

/**
 * An edge a list page reports about the part it keeps in view while a video is open, in pixels of
 * the screen from the top of the page; 0 when it keeps none. Null when the message is no such
 * report.
 */
fun keepBottom(
    type: Any?,
    edge: Any?,
): Int? {
    if (type != "keep" || edge !is Number) return null
    val edge = edge.toDouble()
    return if (edge.isFinite()) edge.roundToInt().coerceAtLeast(0) else null
}

/**
 * How many pixels of the list stay in view above the YouTube screen. `bottom` is where the part
 * the list keeps in view ends, `over` where what that part has opened out of itself ends (a menu
 * under a header), and `height` the room the two screens share.
 *
 * The kept part gets a fifth of the room at most: where that is not enough, as under a keyboard
 * on a screen on its side, YouTube gets all of it. What it opens is there for a moment, at the
 * user's own press, and shows whole. A video that fills the screen leaves nothing of the list.
 */
fun keptAbove(
    bottom: Int,
    over: Int,
    height: Int,
    immersive: Boolean,
): Int {
    if (immersive || bottom <= 0 || bottom > height / 5) return 0
    return maxOf(bottom, minOf(over, height))
}

/**
 * Whether the list going from one address to another brings it to the front. Above a video only
 * its header can be pressed: the user picked another group or a page from its menu there, and
 * means to see it.
 */
fun listMoved(
    front: Screen,
    before: String?,
    now: String?,
): Boolean = front == Screen.VIDEO && before != null && now != null && before != now
