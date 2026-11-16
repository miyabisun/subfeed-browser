package dev.miyabisun.subfeedbrowser

import org.junit.Assert.assertEquals
import org.junit.Test

class RouteTest {
    @Test
    fun feedKeepsItsOwnPagesAndSignInRedirects() {
        assertEquals(Route.Stay, route(Screen.FEED, "https://feed.example/list?page=2"))
        assertEquals(Route.Stay, route(Screen.FEED, "http://homeserver:8080/"))
        assertEquals(Route.Stay, route(Screen.FEED, "https://team.cloudflareaccess.com/cdn-cgi/access/login"))
        assertEquals(Route.Stay, route(Screen.FEED, "https://accounts.google.com/o/oauth2/auth?x=1"))
    }

    @Test
    fun feedSendsYouTubeLinksToTheVideoScreen() {
        val watch = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
        assertEquals(Route.Video(watch), route(Screen.FEED, watch))
        assertEquals(
            Route.Video("https://youtu.be/dQw4w9WgXcQ"),
            route(Screen.FEED, "https://youtu.be/dQw4w9WgXcQ"),
        )
    }

    @Test
    fun videoScreenKeepsYouTubeAndGoogle() {
        assertEquals(Route.Stay, route(Screen.VIDEO, "https://m.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertEquals(Route.Stay, route(Screen.VIDEO, "https://www.youtube.com/@channel"))
        assertEquals(Route.Stay, route(Screen.VIDEO, "https://accounts.google.com/ServiceLogin"))
        assertEquals(Route.Stay, route(Screen.VIDEO, "https://accounts.google.co.jp/accounts/SetSID"))
        assertEquals(Route.Stay, route(Screen.VIDEO, "HTTPS://M.YOUTUBE.COM/"))
    }

    @Test
    fun videoScreenHandsOtherSitesToTheBrowser() {
        val shop = "https://shop.example/item?id=1"
        assertEquals(Route.Browser(shop), route(Screen.VIDEO, shop))
    }

    @Test
    fun newWindowsNeverReplaceTheFeed() {
        val article = "https://news.example/article"
        val watch = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
        assertEquals(Route.Browser(article), route(Screen.FEED, article, newWindow = true))
        assertEquals(Route.Video(watch), route(Screen.FEED, watch, newWindow = true))
        assertEquals(Route.Stay, route(Screen.VIDEO, watch, newWindow = true))
        assertEquals(Route.Browser(article), route(Screen.VIDEO, article, newWindow = true))
        assertEquals(Route.Refuse, route(Screen.FEED, "vnd.youtube:dQw4w9WgXcQ", newWindow = true))
    }

    @Test
    fun googleWindowsTheFeedOpensStayInTheApp() {
        val consent =
            "https://accounts.google.com/o/oauth2/v2/auth?client_id=x" +
                "&redirect_uri=storagerelay%3A%2F%2Fhttps%2Ffeed.example"
        assertEquals(
            "the sign-in of the list shares the one YouTube has",
            Route.Popup(consent),
            route(Screen.FEED, consent, newWindow = true),
        )
        assertEquals(
            Route.Popup("https://accounts.google.co.jp/signin"),
            route(Screen.FEED, "https://accounts.google.co.jp/signin", newWindow = true),
        )
        assertEquals("in the same window it is the list's own sign-in", Route.Stay, route(Screen.FEED, consent))
        assertEquals(
            "a lookalike is another site",
            Route.Browser("https://accounts.google.com.evil.example/"),
            route(Screen.FEED, "https://accounts.google.com.evil.example/", newWindow = true),
        )
    }

    @Test
    fun aWindowOfTheAppKeepsGoogleAndYouTube() {
        assertEquals(Route.Stay, route(Screen.POPUP, "https://accounts.google.com/v3/signin/challenge/pwd"))
        assertEquals(Route.Stay, route(Screen.POPUP, "https://accounts.youtube.com/accounts/SetSID"))
        assertEquals(
            "what asks for one more window loads in place",
            Route.Stay,
            route(Screen.POPUP, "https://myaccount.google.com/", newWindow = true),
        )
        assertEquals(Route.Browser("https://shop.example/"), route(Screen.POPUP, "https://shop.example/"))
        assertEquals(Route.Refuse, route(Screen.POPUP, "intent://accounts.google.com/#Intent;end"))
    }

    @Test
    fun youTubeLinksToOtherSitesGoStraightToTheBrowser() {
        val wrapped =
            "https://www.youtube.com/redirect?event=video_description&redir_token=abc" +
                "&q=https%3A%2F%2Fstudio.blender.org%2Fprojects%2Fbig-buck-bunny%2F%3Fa%3D1&v=aqz-KE-bpKQ"
        val target = "https://studio.blender.org/projects/big-buck-bunny/?a=1"
        assertEquals(Route.Browser(target), route(Screen.VIDEO, wrapped))
        assertEquals(Route.Browser(target), route(Screen.VIDEO, wrapped, newWindow = true))
    }

    @Test
    fun youTubeLinksThatOnlyLookLikeLeavingStay() {
        val redirect = "https://www.youtube.com/redirect"
        assertEquals(Route.Stay, route(Screen.VIDEO, "$redirect?q=https%3A%2F%2Fwww.youtube.com%2Fwatch%3Fv%3Dx"))
        assertEquals(Route.Stay, route(Screen.VIDEO, "$redirect?q=intent%3A%2F%2Fwww.youtube.com%2F%23Intent%3Bend"))
        assertEquals(Route.Stay, route(Screen.VIDEO, "$redirect?event=video_description"))
        assertEquals(Route.Stay, route(Screen.VIDEO, "$redirect?q=%E0%A4%A"))
        assertEquals(
            Route.Stay,
            route(Screen.VIDEO, "https://www.youtube.com/watch?v=x&q=https%3A%2F%2Fshop.example%2F"),
        )
    }

    @Test
    fun lookalikeHostsAreNotYouTube() {
        for (url in listOf(
            "https://youtube.com.evil.example/watch?v=x",
            "https://notyoutube.com/",
            "https://youtube.com@evil.example/",
            "https://evil.example\\@youtube.com/",
            "https://evil.example/?next=https://youtube.com/",
            "https://evil.example#@youtube.com/",
        )) {
            assertEquals(url, Route.Browser(url), route(Screen.VIDEO, url))
            assertEquals(url, Route.Stay, route(Screen.FEED, url))
        }
    }

    @Test
    fun linksMeantForOtherAppsGoNowhere() {
        for (url in listOf(
            "intent://www.youtube.com/watch?v=x#Intent;scheme=vnd.youtube;package=com.google.android.youtube;end",
            "vnd.youtube://www.youtube.com/watch?v=x",
            "vnd.youtube:dQw4w9WgXcQ",
            "market://details?id=com.google.android.youtube",
            "javascript:alert(1)",
            "file:///sdcard/x.html",
            "",
            "not a url",
        )) {
            assertEquals(url, Route.Refuse, route(Screen.VIDEO, url))
            assertEquals(url, Route.Refuse, route(Screen.FEED, url))
        }
    }
}
