package dev.miyabisun.subfeedbrowser

import org.mozilla.geckoview.WebRequestError

/** Why a page did not load, as far as the user can act on it. */
enum class LoadFailure { OFFLINE, ADDRESS_NOT_FOUND, UNREACHABLE, INSECURE, OTHER }

fun loadFailure(code: Int): LoadFailure =
    when (code) {
        WebRequestError.ERROR_OFFLINE -> LoadFailure.OFFLINE

        WebRequestError.ERROR_UNKNOWN_HOST,
        WebRequestError.ERROR_MALFORMED_URI,
        -> LoadFailure.ADDRESS_NOT_FOUND

        WebRequestError.ERROR_CONNECTION_REFUSED,
        WebRequestError.ERROR_NET_TIMEOUT,
        WebRequestError.ERROR_NET_INTERRUPT,
        WebRequestError.ERROR_NET_RESET,
        WebRequestError.ERROR_PROXY_CONNECTION_REFUSED,
        WebRequestError.ERROR_UNKNOWN_PROXY_HOST,
        -> LoadFailure.UNREACHABLE

        WebRequestError.ERROR_SECURITY_SSL,
        WebRequestError.ERROR_SECURITY_BAD_CERT,
        -> LoadFailure.INSECURE

        else -> LoadFailure.OTHER
    }
