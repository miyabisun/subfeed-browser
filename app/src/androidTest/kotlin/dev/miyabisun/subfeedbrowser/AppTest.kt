package dev.miyabisun.subfeedbrowser

import android.app.Activity
import android.app.Instrumentation
import android.app.UiAutomation
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.geckoview.GeckoSession
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.roundToInt

/**
 * The app against a stand-in list and the real YouTube. It needs a network connection.
 * Nothing here signs in to anything.
 */
@RunWith(AndroidJUnit4::class)
class AppTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val settings = Settings(context)
    private val feed = FakeFeed()
    private lateinit var browser: Browser
    private lateinit var leavingTheApp: Instrumentation.ActivityMonitor
    private var scenario: ActivityScenario<MainActivity>? = null

    // How many pixels of the screen one of the page's pixels takes, as the engine counts them.
    private var ratio = 1.0

    @Before
    fun setUp() {
        // Anything that would bring another app to the front is caught here and never starts.
        val filter = IntentFilter(Intent.ACTION_VIEW)
        filter.addCategory(Intent.CATEGORY_BROWSABLE)
        listOf("http", "https", "intent", "vnd.youtube", "market").forEach(filter::addDataScheme)
        leavingTheApp = instrumentation.addMonitor(filter, null, true)
        onMain { browser = Browser.of(context) }
    }

    @After
    fun tearDown() {
        // The browser outlives the Activity: leave it on the list for the next test.
        if (scenario != null) await("the list in front") { backUnlessOnFeed() }
        scenario?.close()
        instrumentation.removeMonitor(leavingTheApp)
        feed.stop()
        settings.feedUrl = null
    }

    @Test
    fun opensVideosFromTheListAndComesBackToTheSamePlace() {
        val page = openFeed()
        assertEquals("the page is told it is inside the app", "1", page.videoHost)

        feed.scrollTo(1500)
        feed.click("open-12")
        await("the first video") { browser.videoUrl.contains("youtube.com/watch?v=aqz-KE-bpKQ") }
        back()
        await("the list in front") { browser.front == Screen.FEED }
        assertEquals("the list was neither reloaded nor moved", page.copy(scrollY = 1500), feed.report())
        assertTrue(shows(R.id.feed) && !shows(R.id.video))

        feed.click("open-13")
        await("the second video") { browser.videoUrl.contains("youtube.com/watch?v=eRsGyueVLvQ") }
        assertTrue(shows(R.id.video) && !shows(R.id.feed))
        back()
        await("the list in front") { browser.front == Screen.FEED }
        assertEquals(page.copy(scrollY = 1500), feed.report())
        assertEquals("nothing left the app", 0, leavingTheApp.hits)
    }

    @Test
    fun keepsTheHeaderOfTheListInViewAboveAVideo() {
        openFeed()
        feed.keep(56)
        feed.click("open-0")
        await("the video below the header") { videoTop() == pixels(56) }
        assertTrue("both show", shows(R.id.feed) && shows(R.id.video))

        feed.keep(120)
        await("the video to follow the header") { videoTop() == pixels(120) }

        feed.menu(200)
        await("the video to make room for the menu of the header") { videoTop() == pixels(320) }
        feed.menu(0)
        await("the video back under the header") { videoTop() == pixels(120) }

        feed.keep(0)
        await("the video over the whole screen") { videoTop() == 0 }
        assertTrue(shows(R.id.video) && !shows(R.id.feed))
    }

    @Test
    fun staysOnTheVideoWhenTheHeaderIsPressedAndShowsTheListWhenItMoves() {
        val page = openFeed()
        feed.keep(56)
        feed.click("open-0")
        await("the video below the header") { videoTop() == pixels(56) }

        touch(pixels(56) / 2f)
        assertEquals("the page got the press", 1, feed.report().presses)
        stays("on the video") { browser.front == Screen.VIDEO }

        feed.go("group")
        await("the list in front") { browser.front == Screen.FEED }
        assertEquals("the list was not loaded again", page.instance, feed.report().instance)
        assertTrue(shows(R.id.feed) && !shows(R.id.video))
    }

    @Test
    fun opensAGoogleWindowOfTheListInsideTheApp() {
        val page = openFeed()
        tap("google-window")
        await("Google's page in a window of the app") { browser.popupUrl.contains("accounts.google.com") }
        assertTrue("it covers the list", shows(R.id.popup) && !shows(R.id.feed))
        await("the window closed") { backUnlessClosed() }
        assertEquals(Screen.FEED, onMain { browser.front })
        assertTrue(shows(R.id.feed) && !shows(R.id.popup))

        // From the header above a video: the window opens over the video, and closes back to it.
        feed.keep(56)
        feed.click("open-0")
        await("the video below the header") { videoTop() == pixels(56) }
        feed.raise("google-window")
        touch(pixels(56) / 2f)
        feed.lower("google-window")
        await("Google's page in a window of the app") { browser.popupUrl.contains("accounts.google.com") }
        assertTrue("it covers the video too", shows(R.id.popup) && !shows(R.id.video) && !shows(R.id.feed))
        await("the window closed") { backUnlessClosed() }
        assertEquals("the video is still open", Screen.VIDEO, onMain { browser.front })
        assertEquals(pixels(56), videoTop())

        assertEquals("the list was not loaded again", page.instance, feed.report().instance)
        assertEquals("nothing left the app", 0, leavingTheApp.hits)
    }

    @Test
    fun letsTheUserPickFromASelectOfTheList() {
        val page = openFeed()
        tap("group")
        await("the choices") { activity().asking?.isShowing == true }
        val shown =
            onMain {
                activity().asking!!.listView.adapter.let { lines ->
                    List(lines.count) { lines.getItem(it) }
                }
            }
        assertEquals(listOf<Any?>("all", "one", "two"), shown)
        onMain { activity().asking!!.listView.run { performItemClick(getChildAt(2), 2, 2) } }
        await("the choices gone") { activity().asking == null }
        assertEquals("the page got the choice", page.copy(group = "2"), feed.report())

        tap("group")
        await("the choices") { activity().asking?.isShowing == true }
        onMain { activity().asking!!.cancel() }
        await("the choices gone") { activity().asking == null }
        assertEquals("closed without a choice, the page keeps what it had", page.copy(group = "2"), feed.report())
    }

    @Test
    fun givesAVideoTheWholeScreenOfAPhoneOnItsSide() {
        assumeTrue("a phone", context.resources.configuration.smallestScreenWidthDp < 600)
        openFeed()
        feed.keep(56)
        // The device itself is turned: the engine lets go of an orientation the Activity asks for.
        val device = instrumentation.uiAutomation
        try {
            device.setRotation(UiAutomation.ROTATION_FREEZE_90)
            feed.click("open-0")
            await("the page to ask for the whole screen") { browser.video?.stage == true }
            await("the video over the whole screen") { browser.immersive && videoTop() == 0 }
            assertTrue("the header of the list is out of the way", shows(R.id.video) && !shows(R.id.feed))
            assertTrue("there is nothing to pull at", !onMain { browser.pulls })

            device.setRotation(UiAutomation.ROTATION_FREEZE_0)
            await("the page under the header again") { browser.video?.stage == false && videoTop() == pixels(56) }
        } finally {
            device.setRotation(UiAutomation.ROTATION_FREEZE_0)
        }
    }

    @Test
    fun readsAYouTubePageAgainWhenItIsPulledDownFromItsTop() {
        val page = openFeed()
        feed.keep(56)
        feed.click("channel")
        await("the page") { browser.pulls }
        val address = onMain { browser.videoUrl }
        val goesBack = onMain { browser.video?.canGoBack }
        val loads = countLoads()

        // Pulled twice in a row: the second pull finds the page being read again.
        swipe(R.id.video, from = 0.4f, to = 0.8f, times = 2)
        await("the page read again") { loads.started.get() > 0 && browser.pulls && !refreshing() }
        assertEquals("a pull while it is read again does not read it once more", 1, loads.started.get())
        assertTrue("the indicator turned while it loaded", loads.indicatorTurned)
        assertEquals("the same page", address, onMain { browser.videoUrl })
        assertEquals("nothing was added to go back to", goesBack, onMain { browser.video?.canGoBack })
        assertEquals("the header of the list stays", pixels(56), videoTop())

        // Read on, and a pull only scrolls the page back up. A page that has just loaded
        // takes a moment before it has anything to scroll.
        val scrolled = AtomicInteger()
        onMain {
            browser.video?.session?.scrollDelegate =
                object : GeckoSession.ScrollDelegate {
                    override fun onScrollChanged(
                        session: GeckoSession,
                        scrollX: Int,
                        scrollY: Int,
                    ) = scrolled.set(scrollY)
                }
        }
        val giveUp = System.currentTimeMillis() + 40_000
        while (scrolled.get() == 0) {
            assertTrue("timed out waiting for the page to scroll", System.currentTimeMillis() < giveUp)
            swipe(R.id.video, from = 0.8f, to = 0.2f)
            Thread.sleep(1_000)
        }
        swipe(R.id.video, from = 0.8f, to = 0.2f)
        swipe(R.id.video, from = 0.4f, to = 0.7f)
        assertTrue("the page is still further down", scrolled.get() > 0)
        stays("a page that is not read again") { loads.started.get() == 1 && !refreshing() }
        assertEquals("the list was not loaded again", page.instance, feed.report().instance)
    }

    @Test
    fun leavesAPullOnASignInPageAlone() {
        openFeed()
        feed.click("sign-in")
        await("Google's sign-in page") {
            browser.videoUrl.contains("accounts.google.com") &&
                browser.video?.progress == 100
        }
        assertTrue(!onMain { browser.pulls })

        val loads = countLoads()
        swipe(R.id.video, from = 0.4f, to = 0.8f)
        stays("a page that is not read again") { loads.started.get() == 0 && !refreshing() }
    }

    @Test
    fun leavesAPullOnTheListToItsPage() {
        val page = openFeed()
        // Once the YouTube screen has been in front, it must be out of the way again.
        feed.click("open-0")
        await("the video") { browser.videoUrl.contains("youtube.com/watch?v=aqz-KE-bpKQ") }
        back()
        await("the list in front") { browser.front == Screen.FEED }

        swipe(R.id.feed, from = 0.4f, to = 0.8f)
        assertEquals("the page got the pull, and was not loaded again", page.copy(pulls = 1), feed.report())
        assertTrue(!onMain { refreshing() })
    }

    @Test
    fun opensAPlainYouTubeLinkInTheVideoScreen() {
        val page = openFeed()
        feed.click("plain")
        await("the video") { browser.videoUrl.contains("youtube.com/watch?v=aqz-KE-bpKQ") }
        back()
        await("the list in front") { browser.front == Screen.FEED }
        assertEquals(page.instance, feed.report().instance)
        assertEquals(0, leavingTheApp.hits)
    }

    @Test
    fun ignoresRequestsThatAreNotYouTubeVideos() {
        val page = openFeed()
        for (id in listOf("other-site", "other-shape")) {
            feed.click(id)
            assertEquals("$id leaves the list in front", Screen.FEED, onMain { browser.front })
            assertEquals("$id leaves the list as it was", page.instance, feed.report().instance)
        }
        for (id in listOf("intent", "vnd")) {
            tap(id)
            assertEquals("$id leaves the list in front", Screen.FEED, onMain { browser.front })
            assertEquals("$id leaves the list as it was", page.instance, feed.report().instance)
        }
        assertEquals("nothing was handed to another app", 0, leavingTheApp.hits)
    }

    @Test
    fun handsALinkThatWantsItsOwnWindowToTheBrowser() {
        val page = openFeed()
        tap("new-window")
        await("the link to leave the app") { leavingTheApp.hits == 1 }
        assertEquals(Screen.FEED, onMain { browser.front })
        assertEquals(page.instance, feed.report().instance)
    }

    @Test
    fun asksForTheAddressOnTheFirstRun() {
        feed.start()
        val asking = instrumentation.addMonitor(SettingsActivity::class.java.name, null, false)
        scenario = ActivityScenario.launch(MainActivity::class.java)
        val setup = asking.waitForActivityWithTimeout(10_000) ?: error("the first run did not ask for the address")
        instrumentation.removeMonitor(asking)

        save(setup, "ftp://feed.example/")
        assertTrue("an address that cannot be opened is refused", onMain { setup.shows(R.id.invalid) })
        assertEquals(null, settings.feedUrl)

        save(setup, feed.url)
        assertEquals(feed.url, settings.feedUrl)
        assertEquals("the saved address opens", "1", feed.report().videoHost)
    }

    @Test
    fun offersARetryWhenTheListCannotBeReached() {
        val port = ServerSocket(0).use { it.localPort }
        settings.feedUrl = "http://127.0.0.1:$port/feed.html?driven"
        scenario = ActivityScenario.launch(MainActivity::class.java)
        await("the failure") { shows(R.id.failure) }
        assertEquals(LoadFailure.UNREACHABLE, onMain { browser.feed.failure?.reason })
        assertTrue("the address can be corrected from here", shows(R.id.change_url))

        feed.start(port)
        onActivity { it.findViewById<Button>(R.id.retry).performClick() }
        feed.report()
        assertTrue(!shows(R.id.failure))
    }

    @Test
    fun loadsTheListByItselfOnceItIsWithinReachAndTheAppComesBack() {
        val port = openFeedOutOfReach()
        assertTrue("the connection is what to check first", shows(R.id.failure_hint))

        // Still out of reach: coming back tries once, and the failure stays to be corrected.
        val failed = onMain { browser.feed.failure }
        comeBackToTheFront()
        await("another try") { browser.feed.failure.let { it != null && it !== failed } }
        val failedAgain = onMain { browser.feed.failure }
        Thread.sleep(3_000)
        assertSame("nothing keeps trying", failedAgain, onMain { browser.feed.failure })
        assertTrue(shows(R.id.failure) && shows(R.id.change_url))

        feed.start(port)
        comeBackToTheFront()
        assertEquals("the list loaded without a touch", "1", feed.report().videoHost)
        assertTrue(!shows(R.id.failure))
    }

    @Test
    fun loadsTheListByItselfOnceItIsWithinReachAndTheNetworkChanges() {
        val port = openFeedOutOfReach()

        val failed = onMain { browser.feed.failure }
        onMain { browser.network(browser.link) }
        Thread.sleep(3_000)
        assertSame("the same network is no reason to try", failed, onMain { browser.feed.failure })

        feed.start(port)
        onMain { browser.network(ANOTHER_NETWORK) }
        assertEquals("the list loaded without a touch", "1", feed.report().videoHost)
        assertTrue(!shows(R.id.failure))
    }

    @Test
    fun triesAVideoThatFailedAgainAndLeavesTheListAlone() {
        val page = openFeed()
        feed.click("no-such-video")
        await("the failure of the video") { browser.video?.failure != null }
        assertTrue("there is no address to correct for a video", shows(R.id.failure) && !shows(R.id.change_url))

        val failed = onMain { browser.video?.failure }
        onMain { browser.network(ANOTHER_NETWORK) }
        await("another try after the network changed") { browser.video?.failure.let { it != null && it !== failed } }
        val failedAgain = onMain { browser.video?.failure }
        comeBackToTheFront()
        await("another try after coming back") { browser.video?.failure.let { it != null && it !== failedAgain } }
        assertEquals("the list was not loaded again", page.instance, feed.report().instance)
    }

    @Test
    fun leavesAListThatShowsAloneWhenTheAppComesBackOrTheNetworkChanges() {
        val page = openFeed()
        feed.scrollTo(1500)
        comeBackToTheFront()
        onMain { browser.network(ANOTHER_NETWORK) }
        onMain { browser.network(null) }
        assertEquals("the list was neither reloaded nor moved", page.copy(scrollY = 1500), feed.report())
    }

    @Test
    fun readsTheListAgainWhenItsAddressIsSaved() {
        val page = openFeed()
        save(openSettings(), feed.url)
        val again = feed.reportAfter(page.instance)

        val other = "${feed.url}&other"
        save(openSettings(), other)
        feed.reportAfter(again.instance)
        assertEquals(other, onMain { browser.feed.url })
    }

    /** Start the app with a list nothing answers for. Returns the port a list can be started on. */
    private fun openFeedOutOfReach(): Int {
        val port = ServerSocket(0).use { it.localPort }
        settings.feedUrl = "http://127.0.0.1:$port/feed.html?driven"
        scenario = ActivityScenario.launch(MainActivity::class.java)
        await("the failure") { shows(R.id.failure) }
        await("the device's network") { browser.link != null }
        return port
    }

    /** Leave the app and return to it, as going to the home screen and back does. */
    private fun comeBackToTheFront() {
        scenario!!.moveToState(Lifecycle.State.CREATED)
        scenario!!.moveToState(Lifecycle.State.RESUMED)
    }

    private fun openSettings(): Activity {
        val opening = instrumentation.addMonitor(SettingsActivity::class.java.name, null, false)
        onActivity { it.startActivity(Intent(it, SettingsActivity::class.java)) }
        val setup = opening.waitForActivityWithTimeout(10_000) ?: error("the settings did not open")
        instrumentation.removeMonitor(opening)
        return setup
    }

    private fun openFeed(): FakeFeed.Report {
        feed.start()
        settings.feedUrl = feed.url
        scenario = ActivityScenario.launch(MainActivity::class.java)
        return feed.report().also { ratio = it.ratio }
    }

    private fun save(
        setup: Activity,
        address: String,
    ) = onMain {
        setup.findViewById<EditText>(R.id.feed_url).setText(address)
        setup.findViewById<Button>(R.id.save).performClick()
    }

    /** Touch a link of the list the way a finger does; scripts cannot open windows or other apps. */
    private fun tap(id: String) {
        feed.raise(id)
        touch(null)
        feed.lower(id)
    }

    /** Touch the list this far below its top, or in its middle. */
    private fun touch(below: Float?) {
        val at = IntArray(2)
        var width = 0
        var height = 0
        onActivity {
            val view = it.findViewById<View>(R.id.feed)
            view.getLocationOnScreen(at)
            width = view.width
            height = view.height
        }
        val x = at[0] + width / 2f
        val y = at[1] + (below ?: (height / 2f))
        val now = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(now, now, action, x, y, 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            instrumentation.sendPointerSync(event)
            event.recycle()
        }
    }

    /** Drag a finger down or up the middle of a screen, between two heights given as shares of its own. */
    private fun swipe(
        id: Int,
        from: Float,
        to: Float,
        times: Int = 1,
    ) {
        val at = IntArray(2)
        var width = 0
        var height = 0
        onActivity {
            val view = it.findViewById<View>(id)
            view.getLocationOnScreen(at)
            width = view.width
            height = view.height
        }
        var down = 0L

        fun send(
            action: Int,
            share: Float,
        ) {
            val event =
                MotionEvent.obtain(
                    down,
                    SystemClock.uptimeMillis(),
                    action,
                    at[0] + width / 2f,
                    at[1] + height * share,
                    0,
                )
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            instrumentation.sendPointerSync(event)
            event.recycle()
        }
        repeat(times) {
            down = SystemClock.uptimeMillis()
            send(MotionEvent.ACTION_DOWN, from)
            for (step in 1..SWIPE_STEPS) {
                // A finger takes its time: the engine answers what a touch is while it moves.
                Thread.sleep(20)
                send(MotionEvent.ACTION_MOVE, from + (to - from) * step / SWIPE_STEPS)
            }
            send(MotionEvent.ACTION_UP, to)
        }
    }

    /** The loads the YouTube screen started, and whether the pull-to-refresh indicator turned as one did. */
    private class Loads {
        val started = AtomicInteger()

        @Volatile
        var indicatorTurned = false
    }

    /** Watches the loads the YouTube screen starts from now on. */
    private fun countLoads(): Loads {
        val loads = Loads()
        onMain {
            val session = browser.video!!.session
            val app = session.progressDelegate!!
            session.progressDelegate =
                object : GeckoSession.ProgressDelegate {
                    override fun onPageStart(
                        session: GeckoSession,
                        url: String,
                    ) {
                        loads.started.incrementAndGet()
                        loads.indicatorTurned = refreshing()
                        app.onPageStart(session, url)
                    }

                    override fun onPageStop(
                        session: GeckoSession,
                        success: Boolean,
                    ) = app.onPageStop(session, success)

                    override fun onProgressChange(
                        session: GeckoSession,
                        progress: Int,
                    ) = app.onProgressChange(session, progress)
                }
        }
        return loads
    }

    /** Whether the pull-to-refresh indicator of the YouTube screen turns. Call it on the main thread. */
    private fun refreshing() = activity().findViewById<PullLayout>(R.id.pull).isRefreshing

    /** How far below the list's top the YouTube screen starts. */
    private fun videoTop(): Int {
        var top = 0
        onActivity {
            top =
                it.findViewById<View>(R.id.video).topOnScreen() - it.findViewById<View>(R.id.feed).topOnScreen()
        }
        return top
    }

    private fun View.topOnScreen() = IntArray(2).also(::getLocationOnScreen)[1]

    /** A length in the page's pixels, in pixels of the screen. */
    private fun pixels(length: Int) = (length * ratio).roundToInt()

    private fun back() = instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)

    private fun backUnlessOnFeed(): Boolean {
        if (browser.popup == null && browser.front == Screen.FEED) return true
        browser.back()
        return false
    }

    /** Google's page may have gone from one address to another: back walks through them first. */
    private fun backUnlessClosed(): Boolean {
        if (browser.popup == null) return true
        browser.back()
        return false
    }

    private fun shows(id: Int): Boolean {
        var shown = false
        onActivity { shown = it.shows(id) }
        return shown
    }

    private fun Activity.shows(id: Int) = findViewById<View>(id).visibility == View.VISIBLE

    private fun onActivity(action: (MainActivity) -> Unit) {
        scenario!!.onActivity(action)
    }

    private fun <T> onMain(read: () -> T): T {
        var value: T? = null
        instrumentation.runOnMainSync { value = read() }
        @Suppress("UNCHECKED_CAST")
        return value as T
    }

    /** Watch for a while that something a pull must not start does not start. */
    private fun stays(
        what: String,
        condition: () -> Boolean,
    ) = repeat(15) {
        assertTrue("it did not stay $what", onMain(condition))
        Thread.sleep(200)
    }

    /** The Activity in front. Call it on the main thread. */
    private fun activity(): MainActivity {
        lateinit var front: MainActivity
        scenario!!.onActivity { front = it }
        return front
    }

    /** Wait for something that depends on the network or on the engine. */
    private fun await(
        what: String,
        condition: () -> Boolean,
    ) {
        val giveUp = System.currentTimeMillis() + 40_000
        while (!onMain(condition)) {
            assertTrue("timed out waiting for $what", System.currentTimeMillis() < giveUp)
            Thread.sleep(200)
        }
    }

    private val Browser.videoUrl get() = video?.url.orEmpty()
    private val Browser.popupUrl get() = popup?.url.orEmpty()

    private companion object {
        /** A network the device is not on, to tell the app that its network changed. */
        val ANOTHER_NETWORK = Link(network = -1, transports = emptySet(), validated = false)

        const val SWIPE_STEPS = 20
    }
}
