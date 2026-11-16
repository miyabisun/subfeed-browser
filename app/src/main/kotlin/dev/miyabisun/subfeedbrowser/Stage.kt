package dev.miyabisun.subfeedbrowser

/**
 * Whether a YouTube page asks for the whole screen for its video, or gives it back: a phone on
 * its side shows the video alone, without the list's header and the system's bars. Null when the
 * message is no such request.
 */
fun stageRequest(
    type: Any?,
    on: Any?,
): Boolean? = if (type == "stage" && on is Boolean) on else null

/** Whether a YouTube page asks to go back, as the back gesture does: its back button was pressed. */
fun asksToGoBack(type: Any?): Boolean = type == "back"
