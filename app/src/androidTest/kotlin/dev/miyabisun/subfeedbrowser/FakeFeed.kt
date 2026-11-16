package dev.miyabisun.subfeedbrowser

import androidx.test.platform.app.InstrumentationRegistry
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * A server with a page that stands in for youtube-sub-feed. The page reports its state several
 * times a second and runs the commands a test queues for it. Stopped, it is a list out of reach;
 * started again on the same port, it is that list back within reach.
 */
class FakeFeed {
    /** What the page said about itself. `instance` changes whenever the page is loaded again. */
    data class Report(
        val instance: String,
        val scrollY: Int,
        val videoHost: String,
        /** The clicks its header got, pressed by the user or made up by a script. */
        val presses: Int = 0,
        /** The pulls down from its top that reached the page. */
        val pulls: Int = 0,
    )

    private var server = MockWebServer()
    private val commands = LinkedBlockingQueue<String>()
    private val reports = LinkedBlockingQueue<Report>()
    private val page =
        InstrumentationRegistry
            .getInstrumentation()
            .context.assets
            .open("feed.html")
            .bufferedReader()
            .use { it.readText() }

    val url get() = "http://127.0.0.1:${server.port}/feed.html?driven"

    fun start(port: Int = 0) {
        server = MockWebServer()
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val address = request.requestUrl ?: return MockResponse().setResponseCode(400)
                    return when (address.encodedPath) {
                        "/feed.html" -> {
                            MockResponse().setHeader("Content-Type", "text/html; charset=utf-8").setBody(page)
                        }

                        "/command" -> {
                            reports +=
                                Report(
                                    address.queryParameter("instance").orEmpty(),
                                    address.queryParameter("y")?.toIntOrNull() ?: -1,
                                    address.queryParameter("host").orEmpty(),
                                    address.queryParameter("presses")?.toIntOrNull() ?: 0,
                                    address.queryParameter("pulls")?.toIntOrNull() ?: 0,
                                )
                            MockResponse()
                                .setHeader(
                                    "Content-Type",
                                    "application/json",
                                ).setBody(commands.poll() ?: "{}")
                        }

                        else -> {
                            MockResponse().setResponseCode(404)
                        }
                    }
                }
            }
        server.start(port)
    }

    fun stop() = server.shutdown()

    /** The page's next report, so one that is newer than this call. */
    fun report(): Report {
        reports.clear()
        return reports.poll(20, TimeUnit.SECONDS) ?: error("the page stopped reporting")
    }

    /** The report of a page that was loaded after the one that calls itself `instance`. */
    fun reportAfter(instance: String): Report {
        val giveUp = System.currentTimeMillis() + 40_000
        while (true) {
            val report = report()
            if (report.instance != instance) return report
            check(System.currentTimeMillis() < giveUp) { "the page was not loaded again" }
        }
    }

    fun click(id: String) = run("""{"click":"$id"}""")

    fun scrollTo(y: Int) = run("""{"scrollTo":$y}""")

    /** Mark the header to stay in view above a video, this tall in the page's pixels. 0 takes the mark off. */
    fun keep(height: Int) = run("""{"keep":$height}""")

    /** Spread an element over the whole page, so a touch anywhere lands on it. */
    fun raise(id: String) = run("""{"raise":"$id"}""")

    fun lower(id: String) = run("""{"lower":"$id"}""")

    private fun run(command: String) {
        commands += command
        // The report after the one that fetched the command proves the page has run it.
        do report() while (commands.isNotEmpty())
        report()
    }
}
