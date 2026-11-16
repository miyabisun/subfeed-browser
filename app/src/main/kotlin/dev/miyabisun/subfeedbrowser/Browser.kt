package dev.miyabisun.subfeedbrowser

import android.content.Context
import android.content.res.Configuration
import org.json.JSONObject
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSession.NavigationDelegate
import org.mozilla.geckoview.GeckoSession.PermissionDelegate
import org.mozilla.geckoview.GeckoSession.PermissionDelegate.ContentPermission
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.OrientationController
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebRequestError

/** A page that did not load. */
data class Failure(
    val url: String,
    val reason: LoadFailure,
)

/** One screen's browsing session and what the app remembers about it. */
class Page(
    val screen: Screen,
) {
    val session = GeckoSession(GeckoSessionSettings.Builder().suspendMediaWhenInactive(true).build())
    var url: String? = null
    var canGoBack = false
    var fullScreen = false
    var progress = 100
    var failure: Failure? = null
    var turnedAway = false

    /** The user pulled the page down to read it again, and that load is not over. */
    var reloading = false
}

/**
 * The browser engine and the two screens. It outlives the Activity, so a rebuilt Activity
 * finds the list and the video as they were.
 */
class Browser private constructor(
    context: Context,
) {
    /** The Activity, while there is one. */
    interface Window {
        /** Show the state of this browser. */
        fun render()

        fun openInBrowser(url: String)

        /** Follow a page's request to hold the screen in one orientation; null lets it turn again. */
        fun holdOrientation(orientation: Int?)

        fun close()
    }

    private val runtime =
        GeckoRuntime.create(
            context,
            GeckoRuntimeSettings
                .Builder()
                .consoleOutput(BuildConfig.DEBUG)
                .remoteDebuggingEnabled(BuildConfig.DEBUG)
                .build(),
        )

    val feed = open(Screen.FEED)
    var video: Page? = null
        private set
    var front = Screen.FEED
        private set
    val frontPage get() = video?.takeIf { front == Screen.VIDEO } ?: feed

    /** Whether pulling the page in front down from its top reads it again now. */
    val pulls get() = frontPage.run { reloadsOnPull(screen, fullScreen, reloading || progress < 100, url) }

    /** Where the part of the list that stays in view above a video ends, as the list reported it. */
    var keep = 0
        private set

    var window: Window? = null
        set(value) {
            field = value
            activate()
        }

    private var feedUrl: String? = null

    /** The network the device reported last. */
    var link: Link? = null
        private set

    // Pages load only once the extension listens, so the first page can already talk to the app.
    private var extensionReady = false
    private var afterExtension: (() -> Unit)? = null

    init {
        runtime.orientationController.delegate =
            object : OrientationController.OrientationDelegate {
                override fun onOrientationLock(orientation: Int): GeckoResult<AllowOrDeny> {
                    window?.holdOrientation(orientation)
                    return GeckoResult.allow()
                }

                override fun onOrientationUnlock() {
                    window?.holdOrientation(null)
                }
            }
        runtime.webExtensionController.installBuiltIn(EXTENSION).accept(
            { extension ->
                if (extension != null) {
                    feed.session.webExtensionController.setMessageDelegate(extension, feedMessages, NATIVE_APP)
                }
                extensionReady()
            },
            { extensionReady() },
        )
    }

    /** Pass on what changed on the device, so pages follow the system's light and dark. */
    fun configurationChanged(configuration: Configuration) = runtime.configurationChanged(configuration)

    /** Show the list at `url`. Loads it only when it is not the list already there. */
    fun showFeed(url: String) {
        if (url == feedUrl) return
        feedUrl = url
        val load = {
            feed.failure = null
            feed.session.loadUri(url)
        }
        if (extensionReady) load() else afterExtension = load
    }

    fun back() {
        val page = frontPage
        when (backAction(front, page.fullScreen, page.canGoBack)) {
            BackAction.EXIT_FULL_SCREEN -> {
                page.session.exitFullScreen()
            }

            BackAction.GO_BACK -> {
                page.failure = null
                page.session.goBack()
            }

            BackAction.SHOW_FEED -> {
                closeVideo()
            }

            BackAction.CLOSE_APP -> {
                window?.close()
            }
        }
        window?.render()
    }

    /** The user saved the address of the list, changed or not: read it now. */
    fun feedSaved(url: String) {
        feedUrl = null
        showFeed(url)
        window?.render()
    }

    /** Load the page in front again after it failed. */
    fun retry() {
        reload(frontPage)
        window?.render()
    }

    /** The user pulled the page in front down from its top and let go. Adds nothing to go back to. */
    fun pulled() {
        val page = frontPage
        if (pulls) {
            page.reloading = true
            page.session.reload()
        }
        window?.render()
    }

    /** The app came back to the front. */
    fun foreground() = nudge(Nudge.Foreground)

    /** The device reported the network it is on; null is none. */
    fun network(now: Link?) {
        val before = link
        link = now
        nudge(Nudge.Network(before, now))
    }

    /** Pages that failed may be within reach now. Both screens: a video is not left failed behind the list. */
    private fun nudge(nudge: Nudge) {
        for (page in listOfNotNull(feed, video)) {
            if (loadsAgain(page.failure != null, nudge)) reload(page)
        }
        window?.render()
    }

    private fun reload(page: Page) {
        val failed = page.failure ?: return
        page.failure = null
        // Past the engine's caches: it remembers for a while that a name did not resolve.
        page.session.load(GeckoSession.Loader().uri(failed.url).flags(GeckoSession.LOAD_FLAGS_BYPASS_CACHE))
    }

    private fun extensionReady() {
        extensionReady = true
        afterExtension?.invoke()
        afterExtension = null
    }

    private fun openVideo(url: String) {
        // A fresh session each time: back then runs out at the video the list opened.
        video?.session?.close()
        val page = open(Screen.VIDEO)
        video = page
        front = Screen.VIDEO
        activate()
        page.session.loadUri(url)
        window?.render()
    }

    private fun closeVideo() {
        video?.session?.close()
        video = null
        front = Screen.FEED
        window?.holdOrientation(null)
        activate()
    }

    /**
     * Pages run while the Activity is visible and rest, with their media paused, while it is not.
     * The list keeps running behind a video: a resting page's scripts stop, and the list may
     * still be recording that the video was opened.
     */
    private fun activate() {
        val visible = window != null
        feed.session.setActive(visible)
        video?.session?.setActive(visible)
    }

    private fun open(screen: Screen): Page {
        val page = Page(screen)
        val session = page.session
        session.navigationDelegate =
            object : NavigationDelegate {
                override fun onLoadRequest(
                    session: GeckoSession,
                    request: NavigationDelegate.LoadRequest,
                ): GeckoResult<AllowOrDeny> {
                    val newWindow = request.target == NavigationDelegate.TARGET_WINDOW_NEW
                    val allow =
                        when (val route = route(screen, request.uri, newWindow)) {
                            Route.Stay -> {
                                // There are no extra windows: what asks for one loads in place.
                                if (newWindow) session.loadUri(request.uri)
                                !newWindow
                            }

                            is Route.Video -> {
                                openVideo(route.url)
                                false
                            }

                            is Route.Browser -> {
                                window?.openInBrowser(route.url)
                                false
                            }

                            Route.Refuse -> {
                                false
                            }
                        }
                    // Turning away a redirect ends the load it belongs to; the engine reports that
                    // as an error, which is no failure to show.
                    page.turnedAway = !allow && request.isRedirect
                    return if (allow) GeckoResult.allow() else GeckoResult.deny()
                }

                override fun onLocationChange(
                    session: GeckoSession,
                    url: String?,
                    permissions: List<ContentPermission>,
                    hasUserGesture: Boolean,
                ) {
                    page.url = url
                }

                override fun onCanGoBack(
                    session: GeckoSession,
                    canGoBack: Boolean,
                ) {
                    page.canGoBack = canGoBack
                }

                override fun onLoadError(
                    session: GeckoSession,
                    uri: String?,
                    error: WebRequestError,
                ): GeckoResult<String>? {
                    if (page.turnedAway) {
                        page.turnedAway = false
                    } else if (uri != null) {
                        page.failure = Failure(uri, loadFailure(error.code))
                    }
                    window?.render()
                    return null
                }
            }
        session.progressDelegate =
            object : GeckoSession.ProgressDelegate {
                override fun onPageStart(
                    session: GeckoSession,
                    url: String,
                ) {
                    // What the list kept in view belongs to the page that is going away.
                    if (screen == Screen.FEED) keep = 0
                }

                override fun onPageStop(
                    session: GeckoSession,
                    success: Boolean,
                ) {
                    // A load that ends early never reports the last of its progress.
                    page.progress = 100
                    page.reloading = false
                    window?.render()
                }

                override fun onProgressChange(
                    session: GeckoSession,
                    progress: Int,
                ) {
                    page.progress = progress
                    window?.render()
                }
            }
        session.contentDelegate =
            object : GeckoSession.ContentDelegate {
                override fun onFullScreen(
                    session: GeckoSession,
                    fullScreen: Boolean,
                ) {
                    page.fullScreen = fullScreen
                    window?.render()
                }

                override fun onCrash(session: GeckoSession) = reopen()

                override fun onKill(session: GeckoSession) = reopen()

                // The engine closed the session; bring its page back.
                private fun reopen() {
                    page.fullScreen = false
                    page.reloading = false
                    session.open(runtime)
                    page.url?.let(session::loadUri)
                    activate()
                    window?.render()
                }
            }
        session.permissionDelegate =
            object : PermissionDelegate {
                override fun onContentPermissionRequest(
                    session: GeckoSession,
                    permission: ContentPermission,
                ): GeckoResult<Int> {
                    val autoplay =
                        permission.permission == PermissionDelegate.PERMISSION_AUTOPLAY_AUDIBLE ||
                            permission.permission == PermissionDelegate.PERMISSION_AUTOPLAY_INAUDIBLE
                    val answer = if (autoplay) ContentPermission.VALUE_ALLOW else ContentPermission.VALUE_PROMPT
                    return GeckoResult.fromValue(answer)
                }
            }
        session.open(runtime)
        return page
    }

    private val feedMessages =
        object : WebExtension.MessageDelegate {
            override fun onMessage(
                nativeApp: String,
                message: Any,
                sender: WebExtension.MessageSender,
            ): GeckoResult<Any>? {
                if (sender.session !== feed.session || !sender.isTopLevel || message !is JSONObject) return null
                val type = message.opt("type")
                openVideoUrl(type, message.opt("url"))?.let(::openVideo)
                keepBottom(type, message.opt("bottom"))?.let { keep = it }
                if (closesVideo(type, front, frontPage.fullScreen)) closeVideo()
                window?.render()
                return null
            }
        }

    companion object {
        private const val EXTENSION = "resource://android/assets/extension/"
        private const val NATIVE_APP = "browser"

        private var instance: Browser? = null

        fun of(context: Context): Browser = instance ?: Browser(context.applicationContext).also { instance = it }
    }
}
