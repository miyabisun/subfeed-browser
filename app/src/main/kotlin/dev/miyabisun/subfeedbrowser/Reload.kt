package dev.miyabisun.subfeedbrowser

/** The network the device is on, as far as a page that could not load cares. */
data class Link(
    val network: Long,
    val transports: Set<Int>,
    val validated: Boolean,
)

/** What may have brought a page that failed within reach. */
sealed interface Nudge {
    /** The app came back to the front. */
    data object Foreground : Nudge

    /** The device reported its network. `before` is what it reported last; null is no network. */
    data class Network(
        val before: Link?,
        val now: Link?,
    ) : Nudge
}

/**
 * Whether a page loads again by itself. Only one that failed does: a page that shows keeps its
 * place and its video. A nudge loads it once, so a page that cannot load stays failed.
 */
fun loadsAgain(
    failed: Boolean,
    nudge: Nudge,
): Boolean =
    failed &&
        when (nudge) {
            Nudge.Foreground -> true
            is Nudge.Network -> nudge.before != nudge.now
        }
