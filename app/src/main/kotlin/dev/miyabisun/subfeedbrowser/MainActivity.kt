package dev.miyabisun.subfeedbrowser

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.Button
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
    private lateinit var changeUrl: Button

    private var askedForUrl = false
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
        changeUrl = findViewById(R.id.change_url)
        val background = getColor(R.color.background)
        feedView.coverUntilFirstPaint(background)
        videoView.coverUntilFirstPaint(background)
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
        render()
    }

    override fun onConfigurationChanged(configuration: Configuration) {
        super.onConfigurationChanged(configuration)
        browser.configurationChanged(configuration)
    }

    override fun onStop() {
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
        show(feedView, browser.feed.session, browser.front == Screen.FEED)
        show(videoView, browser.video?.session, browser.front == Screen.VIDEO)
        val page = browser.frontPage
        val failed = page.failure
        failure.visibility = if (failed == null) View.GONE else View.VISIBLE
        if (failed != null) {
            failureReason.setText(failed.reason.text)
            failureHost.text = WebAddress.parse(failed.url)?.host
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

    private val LoadFailure.text
        get() =
            when (this) {
                LoadFailure.OFFLINE -> R.string.failure_offline
                LoadFailure.ADDRESS_NOT_FOUND -> R.string.failure_address_not_found
                LoadFailure.UNREACHABLE -> R.string.failure_unreachable
                LoadFailure.INSECURE -> R.string.failure_insecure
                LoadFailure.OTHER -> R.string.failure_other
            }
}
