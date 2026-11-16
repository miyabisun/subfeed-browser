package dev.miyabisun.subfeedbrowser

/**
 * The screens of the app: the list, YouTube in front of it, and a window that the list opened
 * for Google, in front of both.
 */
enum class Screen { FEED, VIDEO, POPUP }

/** Where an address that a screen was asked to open ends up. */
sealed interface Route {
    /** Open it in the screen that was asked. */
    data object Stay : Route

    /** Open it in the YouTube screen. */
    data class Video(
        val url: String,
    ) : Route

    /**
     * Open it in a window of the app's own, joined to the page that asked for it. A sign-in
     * there is the one YouTube has, and the window can answer the page that opened it.
     */
    data class Popup(
        val url: String,
    ) : Route

    /** Hand it to the device's default browser. */
    data class Browser(
        val url: String,
    ) : Route

    /** Open it nowhere: links meant for other apps end here. */
    data object Refuse : Route
}

/** `newWindow` is true for links and scripts that ask for a window of their own. */
fun route(
    screen: Screen,
    url: String,
    newWindow: Boolean = false,
): Route {
    val address = WebAddress.parse(url) ?: return Route.Refuse
    return when (screen) {
        Screen.FEED -> {
            when {
                address.isYouTube -> Route.Video(url)

                // A window for Google is the list asking the user to sign in or to agree, and it
                // waits for that window's answer. In another browser the user would sign in a
                // second time, and the answer would never come back.
                newWindow && address.isGoogle -> Route.Popup(url)

                // The feed's own sign-in may pass through any site, so its redirects stay.
                // A new window would replace the list, so it goes out instead.
                newWindow -> Route.Browser(url)

                else -> Route.Stay
            }
        }

        Screen.VIDEO, Screen.POPUP -> {
            val outside = address.leavesYouTubeFor
            when {
                // Loading YouTube's forwarding page here would leave an empty page behind.
                outside != null -> Route.Browser(outside)

                address.isYouTube || address.isGoogle -> Route.Stay

                else -> Route.Browser(url)
            }
        }
    }
}

/** The address a feed page asked to open, or null when the message is not such a request. */
fun openVideoUrl(
    type: Any?,
    url: Any?,
): String? {
    if (type != "open-video" || url !is String) return null
    return url.takeIf { WebAddress.parse(it)?.isYouTube == true }
}

/** The feed address to save for what the user typed, or null when it cannot be opened. */
fun feedUrl(input: String): String? {
    val typed = input.trim()
    val url = if ("://" in typed) typed else "https://$typed"
    return url.takeIf { WebAddress.parse(it) != null }
}
