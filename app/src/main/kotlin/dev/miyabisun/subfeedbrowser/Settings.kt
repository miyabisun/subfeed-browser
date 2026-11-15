package dev.miyabisun.subfeedbrowser

import android.content.Context

/** What the user set up and the app keeps across restarts. */
class Settings(
    context: Context,
) {
    private val stored = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** The address of the list of videos, or null before the first setup. */
    var feedUrl: String?
        get() = stored.getString(FEED_URL, null)
        set(value) = stored.edit().putString(FEED_URL, value).apply()

    private companion object {
        const val FEED_URL = "feed_url"
    }
}
