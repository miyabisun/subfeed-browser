package dev.miyabisun.subfeedbrowser

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import org.mozilla.geckoview.GeckoView

/**
 * The system's pull-to-refresh around a page. A touch becomes a pull only once the engine has
 * answered that the page leaves it to the app: the page is at its top and does not use the
 * touch itself. Until then, and for any other touch, everything goes to the page.
 */
class PullLayout(
    context: Context,
    attributes: AttributeSet?,
) : SwipeRefreshLayout(context, attributes) {
    // Open between touches, so the layout notes where the next one starts.
    private var open = true

    // Counts touches, to drop an answer that arrives after its touch is over.
    private var touch = 0
    private var startX = 0f
    private var startY = 0f

    init {
        setOnChildScrollUpCallback { _, _ -> !open }
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        for (n in 0 until childCount) (getChildAt(n) as? GeckoView)?.let(::ask)
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            startX = event.x
            startY = event.y
        }
        val pull = movesLikeAPull(event.pointerCount, event.x - startX, event.y - startY)
        if (event.actionMasked == MotionEvent.ACTION_MOVE && !pull) return false
        return super.onInterceptTouchEvent(event)
    }

    /**
     * Ask the engine about each touch on the page, in place of only passing it on. Nothing here
     * is a click: taps stay the page's, and the engine serves accessibility itself.
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun ask(page: GeckoView) {
        page.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    val asked = ++touch
                    open = false
                    page.onTouchEventForDetailResult(event).accept { answer ->
                        if (asked == touch && answer != null) {
                            open =
                                pullsFromTheTop(
                                    answer.handledResult(),
                                    answer.scrollableDirections(),
                                    answer.overscrollDirections(),
                                )
                        }
                    }
                    true
                }

                // A second finger: the touch is no pull any more, whatever the engine answers.
                MotionEvent.ACTION_POINTER_DOWN -> {
                    touch++
                    open = false
                    false
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    touch++
                    open = true
                    false
                }

                else -> {
                    false
                }
            }
        }
    }
}
