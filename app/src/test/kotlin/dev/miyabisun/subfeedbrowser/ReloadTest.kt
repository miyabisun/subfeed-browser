package dev.miyabisun.subfeedbrowser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReloadTest {
    private val wifi = Link(network = 1, transports = setOf(1), validated = true)
    private val vpn = Link(network = 2, transports = setOf(1, 4), validated = true)

    @Test
    fun loadsAFailedPageAgainWhenTheAppComesBackToTheFront() {
        assertTrue(loadsAgain(failed = true, Nudge.Foreground))
    }

    @Test
    fun loadsAFailedPageAgainWhenTheNetworkChanges() {
        assertTrue("the VPN connects", loadsAgain(failed = true, Nudge.Network(wifi, vpn)))
        assertTrue("the VPN drops", loadsAgain(failed = true, Nudge.Network(vpn, wifi)))
        assertTrue("another Wi-Fi", loadsAgain(failed = true, Nudge.Network(wifi, wifi.copy(network = 3))))
        assertTrue("the first network", loadsAgain(failed = true, Nudge.Network(null, wifi)))
        assertTrue("no network left", loadsAgain(failed = true, Nudge.Network(wifi, null)))
        assertTrue(
            "the network reaches the internet now",
            loadsAgain(failed = true, Nudge.Network(wifi.copy(validated = false), wifi)),
        )
        assertTrue(
            "the VPN moves to mobile data",
            loadsAgain(failed = true, Nudge.Network(vpn, vpn.copy(transports = setOf(0, 4)))),
        )
    }

    @Test
    fun leavesAFailedPageAloneWhileTheNetworkIsTheSame() {
        // The device repeats its report for changes that open no way to a page, such as the signal.
        assertFalse(loadsAgain(failed = true, Nudge.Network(wifi, wifi.copy())))
        assertFalse(loadsAgain(failed = true, Nudge.Network(null, null)))
    }

    @Test
    fun neverLoadsAPageThatShowsAgain() {
        assertFalse(loadsAgain(failed = false, Nudge.Foreground))
        assertFalse(loadsAgain(failed = false, Nudge.Network(wifi, vpn)))
        assertFalse(loadsAgain(failed = false, Nudge.Network(wifi, null)))
        assertFalse(loadsAgain(failed = false, Nudge.Network(null, wifi)))
    }
}
