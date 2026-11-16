package dev.miyabisun.subfeedbrowser

import kotlin.math.roundToInt

/**
 * The bottom edge of the part a list page keeps in view while a video is open, in pixels of the
 * screen from the top of the page; 0 when it keeps none. Null when the message is no such report.
 */
fun keepBottom(
    type: Any?,
    bottom: Any?,
): Int? {
    if (type != "keep" || bottom !is Number) return null
    val edge = bottom.toDouble()
    return if (edge.isFinite()) edge.roundToInt().coerceAtLeast(0) else null
}

/**
 * How many pixels of the list stay in view above the YouTube screen. `bottom` is what the list
 * reported and `height` the room the two share. The list gets a fifth of it at most: where that
 * is not enough, as under a keyboard on a screen on its side, YouTube gets all of it.
 */
fun keptAbove(
    bottom: Int,
    height: Int,
    fullScreen: Boolean,
): Int = if (fullScreen || bottom > height / 5) 0 else bottom

/** Whether this message from the list closes the YouTube screen: a press on the part kept in view. */
fun closesVideo(
    type: Any?,
    front: Screen,
    fullScreen: Boolean,
): Boolean = type == "keep-pressed" && front == Screen.VIDEO && !fullScreen
