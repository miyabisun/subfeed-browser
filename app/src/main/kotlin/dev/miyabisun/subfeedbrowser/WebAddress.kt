package dev.miyabisun.subfeedbrowser

import java.net.URLDecoder

/** An http(s) address, with its host read the way a browser reads it. */
class WebAddress private constructor(
    val host: String,
    private val path: String,
    private val query: String,
) {
    val isYouTube get() = host.isOneOf(YOUTUBE_SITES)
    val isGoogle get() = host.isOneOf(GOOGLE_SITES)

    /**
     * YouTube wraps links to other sites in a page of its own that forwards to them.
     * This is the address such a link leads to, or null when this is no such link.
     */
    val leavesYouTubeFor: String?
        get() {
            if (!isYouTube || path != "/redirect") return null
            val wrapped = query.split('&').firstOrNull { it.startsWith("q=") } ?: return null
            val target =
                try {
                    URLDecoder.decode(wrapped.removePrefix("q="), "UTF-8")
                } catch (_: IllegalArgumentException) {
                    return null
                }
            val address = parse(target) ?: return null
            return target.takeUnless { address.isYouTube || address.isGoogle }
        }

    companion object {
        private val YOUTUBE_SITES = listOf("youtube.com", "youtu.be", "youtube-nocookie.com")

        // Signing in to YouTube passes through these.
        private val GOOGLE_SITES = listOf("google.com", "google.co.jp")

        private val SHAPE = Regex("""^https?://([^/\\?#]*)([^?#]*)(?:\?([^#]*))?""", RegexOption.IGNORE_CASE)
        private val HOST = Regex("""^[a-z0-9]([a-z0-9.-]*[a-z0-9])?$""")
        private val PORT = Regex("""^[0-9]{1,5}$""")

        /** Null for anything that is not an http(s) address with a host. */
        fun parse(url: String): WebAddress? {
            if (url.any { it.isWhitespace() }) return null
            val (authority, path, query) = SHAPE.find(url)?.destructured ?: return null
            val hostAndPort = authority.substringAfterLast('@')
            val host = hostAndPort.substringBefore(':').lowercase()
            val port = hostAndPort.substringAfter(':', "")
            if (!HOST.matches(host)) return null
            if (':' in hostAndPort && !PORT.matches(port)) return null
            return WebAddress(host, path, query)
        }

        private fun String.isOneOf(sites: List<String>) = sites.any { this == it || endsWith(".$it") }
    }
}
