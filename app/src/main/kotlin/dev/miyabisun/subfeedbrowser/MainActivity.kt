package dev.miyabisun.subfeedbrowser

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.SurfaceView
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView

/** The list and, in front of it while a video is open, YouTube. */
class MainActivity :
    Activity(),
    Browser.Window {
    private lateinit var settings: Settings
    private lateinit var browser: Browser
    private lateinit var root: View
    private lateinit var feedView: GeckoView
    private lateinit var videoView: GeckoView
    private lateinit var progress: ProgressBar
    private lateinit var failure: View
    private lateinit var failureReason: TextView
    private lateinit var failureHost: TextView
    private lateinit var failureHint: TextView
    private lateinit var changeUrl: Button

    private var askedForUrl = false
    private var focused: Screen? = null
    private var systemBars = WindowInsets.CONSUMED

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        setContentView(R.layout.main)
        settings = Settings(this)
        browser = Browser.of(this)
        browser.configurationChanged(resources.configuration)
        root = findViewById(android.R.id.content)
        feedView = findViewById(R.id.feed)
        videoView = findViewById(R.id.video)
        progress = findViewById(R.id.progress)
        failure = findViewById(R.id.failure)
        failureReason = findViewById(R.id.failure_reason)
        failureHost = findViewById(R.id.failure_host)
        failureHint = findViewById(R.id.failure_hint)
        changeUrl = findViewById(R.id.change_url)
        val background = getColor(R.color.background)
        feedView.coverUntilFirstPaint(background)
        videoView.coverUntilFirstPaint(background)
        // The list's header shows above a video, so both pages are on the screen at once:
        // YouTube's surface goes over the list's.
        for (n in 0 until videoView.childCount) (videoView.getChildAt(n) as? SurfaceView)?.setZOrderMediaOverlay(true)
        // The room for the two pages changes with the keyboard and when the device turns.
        feedView.addOnLayoutChangeListener { view, _, top, _, bottom, _, oldTop, _, oldBottom ->
            if (bottom - top != oldBottom - oldTop) view.post(::render)
        }
        findViewById<Button>(R.id.retry).setOnClickListener { browser.retry() }
        changeUrl.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        root.setOnApplyWindowInsetsListener { _, insets ->
            systemBars = insets
            pad()
            WindowInsets.CONSUMED
        }
        onBackInvokedDispatcher.registerOnBackInvokedCallback(0) { browser.back() }
    }

    override fun onStart() {
        super.onStart()
        getSystemService(ConnectivityManager::class.java)
            .registerDefaultNetworkCallback(network, Handler(Looper.getMainLooper()))
        val url = settings.feedUrl
        when {
            url != null -> {
                browser.showFeed(url)
            }

            // Left the first setup without an address: there is nothing to show.
            askedForUrl -> {
                return finish()
            }

            else -> {
                askedForUrl = true
                startActivity(Intent(this, SettingsActivity::class.java))
            }
        }
        browser.window = this
        browser.foreground()
        render()
    }

    override fun onConfigurationChanged(configuration: Configuration) {
        super.onConfigurationChanged(configuration)
        browser.configurationChanged(configuration)
    }

    override fun onStop() {
        getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(network)
        browser.window = null
        super.onStop()
    }

    override fun onDestroy() {
        // The sessions stay with the browser; a rebuilt Activity picks them up again.
        feedView.releaseSession()
        videoView.releaseSession()
        super.onDestroy()
    }

    override fun render() {
        val page = browser.frontPage
        val video = page.screen == Screen.VIDEO
        // The top of the list that stays in view above YouTube: its header, if it marks one.
        val kept = if (video) keptAbove(browser.keep, feedView.height, page.fullScreen) else 0
        show(feedView, browser.feed.session, !video || kept > 0)
        show(videoView, browser.video?.session, video)
        for (view in listOf(videoView, progress, failure)) lower(view, kept)
        if (focused != page.screen) {
            // The keyboard types into the page in front, though the list may still show above it.
            focused = page.screen
            (if (video) videoView else feedView).requestFocus()
        }
        val failed = page.failure
        failure.visibility = if (failed == null) View.GONE else View.VISIBLE
        if (failed != null) {
            failureReason.setText(failed.reason.text)
            failureHost.text = WebAddress.parse(failed.url)?.host
            failureHint.visibility = if (failed.reason.pointsAtConnection) View.VISIBLE else View.GONE
            changeUrl.visibility = if (page.screen == Screen.FEED) View.VISIBLE else View.GONE
        }
        progress.progress = page.progress
        progress.visibility = if (failed == null && page.progress < 100) View.VISIBLE else View.GONE
        window.insetsController?.let { bars ->
            bars.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            val all = WindowInsets.Type.systemBars()
            if (page.fullScreen) bars.hide(all) else bars.show(all)
        }
        pad()
    }

    override fun openInBrowser(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE))
        } catch (_: ActivityNotFoundException) {
            // No app on this device opens web pages; the link stays where it is.
        }
    }

    override fun holdOrientation(orientation: Int?) {
        requestedOrientation = orientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

    override fun close() = finish()

    private fun show(
        view: GeckoView,
        session: GeckoSession?,
        inFront: Boolean,
    ) {
        if (view.session !== session) {
            view.releaseSession()
            if (session != null) view.setSession(session)
        }
        view.visibility = if (inFront) View.VISIBLE else View.INVISIBLE
    }

    /** Put what belongs to the page in front below the part of the list that stays in view. */
    private fun lower(
        view: View,
        top: Int,
    ) {
        val place = view.layoutParams as FrameLayout.LayoutParams
        if (place.topMargin == top) return
        place.topMargin = top
        view.layoutParams = place
    }

    /** Keep pages clear of the system bars and the keyboard, except a full-screen video. */
    private fun pad() {
        val types = WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout() or WindowInsets.Type.ime()
        val inset = systemBars.getInsets(types)
        if (browser.frontPage.fullScreen) {
            root.setPadding(0, 0, 0, 0)
        } else {
            root.setPadding(inset.left, inset.top, inset.right, inset.bottom)
        }
    }

    /** Tells the browser the network the app's pages load over, first right after it is registered. */
    private val network =
        object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(
                network: Network,
                capabilities: NetworkCapabilities,
            ) = browser.network(
                Link(
                    network.networkHandle,
                    TRANSPORTS.filter(capabilities::hasTransport).toSet(),
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
                ),
            )

            override fun onLost(network: Network) = browser.network(null)
        }

    private val LoadFailure.text
        get() =
            when (this) {
                LoadFailure.OFFLINE -> R.string.failure_offline
                LoadFailure.ADDRESS_NOT_FOUND -> R.string.failure_address_not_found
                LoadFailure.UNREACHABLE -> R.string.failure_unreachable
                LoadFailure.INSECURE -> R.string.failure_insecure
                LoadFailure.OTHER -> R.string.failure_other
            }

    private companion object {
        val TRANSPORTS =
            listOf(
                NetworkCapabilities.TRANSPORT_CELLULAR,
                NetworkCapabilities.TRANSPORT_WIFI,
                NetworkCapabilities.TRANSPORT_ETHERNET,
                NetworkCapabilities.TRANSPORT_BLUETOOTH,
                NetworkCapabilities.TRANSPORT_USB,
                NetworkCapabilities.TRANSPORT_VPN,
            )
    }
}
