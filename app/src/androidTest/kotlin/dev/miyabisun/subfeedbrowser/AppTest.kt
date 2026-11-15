package dev.miyabisun.subfeedbrowser

import android.app.Activity
import android.app.Instrumentation
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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.net.ServerSocket

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
        return feed.report()
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
        val y = at[1] + height / 2f
        val now = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(now, now, action, x, y, 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            instrumentation.sendPointerSync(event)
            event.recycle()
        }
        feed.lower(id)
    }

    private fun back() = instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)

    private fun backUnlessOnFeed(): Boolean {
        if (browser.front == Screen.FEED) return true
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

    private companion object {
        /** A network the device is not on, to tell the app that its network changed. */
        val ANOTHER_NETWORK = Link(network = -1, transports = emptySet(), validated = false)
    }
}
