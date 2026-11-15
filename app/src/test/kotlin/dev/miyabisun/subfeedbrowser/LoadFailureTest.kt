package dev.miyabisun.subfeedbrowser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mozilla.geckoview.WebRequestError

class LoadFailureTest {
    @Test
    fun tellsApartWhatTheUserCanFix() {
        assertEquals(LoadFailure.OFFLINE, loadFailure(WebRequestError.ERROR_OFFLINE))
        assertEquals(LoadFailure.ADDRESS_NOT_FOUND, loadFailure(WebRequestError.ERROR_UNKNOWN_HOST))
        assertEquals(LoadFailure.ADDRESS_NOT_FOUND, loadFailure(WebRequestError.ERROR_MALFORMED_URI))
        assertEquals(LoadFailure.UNREACHABLE, loadFailure(WebRequestError.ERROR_CONNECTION_REFUSED))
        assertEquals(LoadFailure.UNREACHABLE, loadFailure(WebRequestError.ERROR_NET_TIMEOUT))
        assertEquals(LoadFailure.INSECURE, loadFailure(WebRequestError.ERROR_SECURITY_BAD_CERT))
        assertEquals(LoadFailure.INSECURE, loadFailure(WebRequestError.ERROR_SECURITY_SSL))
    }

    @Test
    fun fallsBackToAPlainFailure() {
        assertEquals(LoadFailure.OTHER, loadFailure(WebRequestError.ERROR_UNKNOWN))
        assertEquals(LoadFailure.OTHER, loadFailure(-1))
    }

    @Test
    fun pointsAtTheConnectionWhenTheServerCouldNotBeFoundOrReached() {
        assertTrue(LoadFailure.OFFLINE.pointsAtConnection)
        assertTrue(LoadFailure.ADDRESS_NOT_FOUND.pointsAtConnection)
        assertTrue(LoadFailure.UNREACHABLE.pointsAtConnection)
        assertFalse(LoadFailure.INSECURE.pointsAtConnection)
        assertFalse(LoadFailure.OTHER.pointsAtConnection)
    }
}
