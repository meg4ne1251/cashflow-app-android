package com.kakeibo.android.core.network.auth

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 0 in-memory CookieJar. Phase 1 replaces this with an EncryptedSharedPreferences
 * backed implementation so refresh_token survives process death.
 */
@Singleton
class AuthCookieJar @Inject constructor() : CookieJar {

    private val cookies = ConcurrentHashMap<String, MutableList<Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val key = url.host
        val bucket = this.cookies.getOrPut(key) { mutableListOf() }
        synchronized(bucket) {
            cookies.forEach { newCookie ->
                bucket.removeAll { it.name == newCookie.name }
                bucket.add(newCookie)
            }
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val bucket = cookies[url.host] ?: return emptyList()
        synchronized(bucket) {
            val now = System.currentTimeMillis()
            bucket.removeAll { it.expiresAt < now }
            return bucket.filter { it.matches(url) }.toList()
        }
    }

    fun clear() {
        cookies.clear()
    }
}
