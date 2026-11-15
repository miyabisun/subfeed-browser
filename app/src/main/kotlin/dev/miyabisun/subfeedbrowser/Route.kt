package dev.miyabisun.subfeedbrowser

/** The two screens of the app. */
enum class Screen { FEED, VIDEO }

/** Where an address that a screen was asked to open ends up. */
sealed interface Route {
    /** Open it in the screen that was asked. */
    data object Stay : Route

    /** Open it in the YouTube screen. */
    data class Video(
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

                // The feed's own sign-in may pass through any site, so its redirects stay.
                // A new window would replace the list, so it goes out instead.
                newWindow -> Route.Browser(url)

                else -> Route.Stay
            }
        }

        Screen.VIDEO -> {
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
