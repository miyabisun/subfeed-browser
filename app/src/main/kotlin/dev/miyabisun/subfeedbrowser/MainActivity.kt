package dev.miyabisun.subfeedbrowser

import android.app.Activity
import android.app.AlertDialog
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
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.ArrayAdapter
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
    private lateinit var popupView: GeckoView
    private lateinit var pull: PullLayout
    private lateinit var progress: ProgressBar
    private lateinit var failure: View
    private lateinit var failureReason: TextView
    private lateinit var failureHost: TextView
    private lateinit var failureHint: TextView
    private lateinit var changeUrl: Button

    /** The question of a page that is up for the user to answer, if any. */
    internal var asking: AlertDialog? = null
        private set

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
        popupView = findViewById(R.id.popup)
        pull = findViewById(R.id.pull)
        progress = findViewById(R.id.progress)
        failure = findViewById(R.id.failure)
        failureReason = findViewById(R.id.failure_reason)
        failureHost = findViewById(R.id.failure_host)
        failureHint = findViewById(R.id.failure_hint)
        changeUrl = findViewById(R.id.change_url)
        val background = getColor(R.color.background)
        feedView.coverUntilFirstPaint(background)
        videoView.coverUntilFirstPaint(background)
        popupView.coverUntilFirstPaint(background)
        // The list's header shows above a video, so both pages are on the screen at once:
        // YouTube's surface goes over the list's.
        for (n in 0 until videoView.childCount) (videoView.getChildAt(n) as? SurfaceView)?.setZOrderMediaOverlay(true)
        // The room for the two pages changes with the keyboard and when the device turns.
        feedView.addOnLayoutChangeListener { view, _, top, _, bottom, _, oldTop, _, oldBottom ->
            if (bottom - top != oldBottom - oldTop) view.post(::render)
        }
        pull.setColorSchemeColors(getColor(R.color.accent))
        pull.setProgressBackgroundColorSchemeColor(getColor(R.color.surface))
        pull.setOnRefreshListener { browser.pulled() }
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
        // A page whose question goes down with the Activity hears that nothing was answered.
        asking?.dismiss()
        feedView.releaseSession()
        videoView.releaseSession()
        popupView.releaseSession()
        super.onDestroy()
    }

    override fun render() {
        val page = browser.frontPage
        // A window the list opened covers the list and YouTube; they come back when it closes.
        val popup = page.screen == Screen.POPUP
        val video = page.screen == Screen.VIDEO
        val immersive = browser.immersive
        // The top of the list that stays in view above YouTube: its header, if it marks one,
        // and what the header has opened under itself.
        val kept = if (video) keptAbove(browser.keep, browser.keepOver, feedView.height, immersive) else 0
        show(feedView, browser.feed.session, !popup && (!video || kept > 0))
        show(videoView, browser.video?.session, video)
        show(popupView, browser.popup?.session, popup)
        pull.visibility = videoView.visibility
        for (view in listOf(pull, progress, failure)) lower(view, kept)
        // The indicator turns until the page is read again; the layout takes no pull meanwhile.
        val pulls = browser.pulls || page.reloading
        if (pull.isEnabled != pulls) pull.isEnabled = pulls
        pull.isRefreshing = page.reloading
        if (focused != page.screen) {
            // The keyboard types into the page in front, though the list may still show above it.
            focused = page.screen
            when (page.screen) {
                Screen.FEED -> feedView
                Screen.VIDEO -> videoView
                Screen.POPUP -> popupView
            }.requestFocus()
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
            if (immersive) bars.hide(all) else bars.show(all)
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

    override fun choose(
        title: String?,
        rows: List<ChoiceRow>,
        many: Boolean,
        done: (List<String>?) -> Unit,
    ) {
        val labels = rows.map { it.label }
        val builder = AlertDialog.Builder(this).setTitle(title?.takeIf(String::isNotBlank))
        var picked: List<String>? = null
        if (many) {
            val checked = rows.map { it.checked }.toBooleanArray()
            builder
                .setMultiChoiceItems(labels.toTypedArray(), checked) { _, row, on -> checked[row] = on }
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    picked = rows.filterIndexed { row, _ -> checked[row] }.mapNotNull { it.id }
                }.setNegativeButton(android.R.string.cancel, null)
        } else {
            // The name of a group and an option the page turned off show, greyed, and take no press.
            val lines =
                object : ArrayAdapter<String>(this, android.R.layout.select_dialog_singlechoice, labels) {
                    override fun areAllItemsEnabled() = false

                    override fun isEnabled(row: Int) = rows[row].id != null

                    override fun getView(
                        row: Int,
                        reused: View?,
                        parent: ViewGroup,
                    ) = super.getView(row, reused, parent).also { it.isEnabled = isEnabled(row) }
                }
            builder.setSingleChoiceItems(lines, rows.indexOfFirst { it.checked }) { dialog, row ->
                picked = listOfNotNull(rows[row].id)
                dialog.dismiss()
            }
        }
        val dialog = builder.create()
        put(dialog) { done(picked) }
    }

    override fun tell(
        message: String?,
        done: () -> Unit,
    ) = put(
        AlertDialog
            .Builder(this)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .create(),
        done,
    )

    override fun ask(
        message: String?,
        done: (Boolean?) -> Unit,
    ) {
        var agreed: Boolean? = null
        val dialog =
            AlertDialog
                .Builder(this)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok) { _, _ -> agreed = true }
                .setNegativeButton(android.R.string.cancel) { _, _ -> agreed = false }
                .create()
        put(dialog) { done(agreed) }
    }

    /** Put a page's question up. However it goes away, the page hears `gone` once. */
    private fun put(
        dialog: AlertDialog,
        gone: () -> Unit,
    ) {
        asking?.dismiss()
        asking = dialog
        dialog.setOnDismissListener {
            if (asking === dialog) asking = null
            gone()
        }
        dialog.show()
    }

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

    /**
     * Keep pages clear of the system bars and the keyboard. A video that has the whole screen
     * goes under the bars and around the camera, and only makes way for the keyboard.
     */
    private fun pad() {
        val keyboard = WindowInsets.Type.ime()
        val types = WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout() or keyboard
        val inset = systemBars.getInsets(types)
        if (browser.immersive) {
            root.setPadding(0, 0, 0, systemBars.getInsets(keyboard).bottom)
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
