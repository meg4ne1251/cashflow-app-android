package com.kakeibo.android.core.network.auth

import com.kakeibo.android.di.RefreshOkHttpClient
import dagger.Lazy
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Authenticator
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On 401, calls POST /api/v1/auth/refresh once, then retries the original request with
 * the refreshed cookies. In-flight 401s are coalesced behind a mutex so we only hit
 * /refresh once per outage. Skips the auth endpoints themselves to avoid loops.
 */
@Singleton
class RefreshAuthenticator @Inject constructor(
    private val cookieJar: PersistentCookieJar,
    @RefreshOkHttpClient private val refreshClient: Lazy<OkHttpClient>,
    private val sessionInvalidator: SessionInvalidator,
) : Authenticator {

    private val mutex = Mutex()

    override fun authenticate(route: Route?, response: Response): Request? {
        val request = response.request
        val path = request.url.encodedPath
        if (path.contains("/auth/login") ||
            path.contains("/auth/refresh") ||
            path.contains("/auth/setup") ||
            path.contains("/auth/logout")
        ) {
            return null
        }

        if (responseCount(response) >= 2) {
            Timber.w("Auth retry exceeded; giving up on %s", request.url)
            sessionInvalidator.onSessionLost()
            return null
        }

        if (!cookieJar.hasRefreshToken()) {
            sessionInvalidator.onSessionLost()
            return null
        }

        val refreshed = runBlocking {
            mutex.withLock { performRefresh(request) }
        }

        return if (refreshed) {
            request.newBuilder().build()
        } else {
            sessionInvalidator.onSessionLost()
            null
        }
    }

    private fun performRefresh(originalRequest: Request): Boolean {
        val refreshUrl = originalRequest.url.newBuilder()
            .encodedPath("/api/v1/auth/refresh")
            .query(null)
            .build()
        val refreshRequest = Request.Builder()
            .url(refreshUrl)
            .post(EMPTY_JSON_BODY)
            .build()

        return runCatching {
            refreshClient.get().newCall(refreshRequest).execute().use { it.isSuccessful }
        }.onFailure {
            Timber.w(it, "Auth refresh failed")
        }.getOrDefault(false)
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }

    companion object {
        private val EMPTY_JSON_BODY = "{}".toRequestBody("application/json".toMediaType())
    }
}
