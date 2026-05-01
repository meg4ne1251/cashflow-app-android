package com.kakeibo.android.core.network.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists OkHttp cookies in EncryptedSharedPreferences so the refresh_token survives
 * process death. Cookies are bucketed per host. Backend currently issues cookies under
 * a single API host so the file size stays small.
 */
@Singleton
class PersistentCookieJar @Inject constructor(
    @ApplicationContext context: Context,
) : CookieJar {

    private val prefs: SharedPreferences = createEncryptedPrefs(context)

    /**
     * All cookie state — cache and disk writes — is guarded by [lock]. A single monitor
     * keeps [clear] from racing with concurrent [saveFromResponse] writes (which would
     * otherwise persist a fresh cookie back to disk after logout).
     */
    private val lock = Any()
    private val cache: MutableMap<String, MutableList<Cookie>> = HashMap()

    init {
        synchronized(lock) { loadAllFromDisk() }
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        synchronized(lock) {
            val host = url.host
            val bucket = cache.getOrPut(host) { mutableListOf() }
            cookies.forEach { incoming ->
                bucket.removeAll { it.name == incoming.name && it.path == incoming.path }
                if (incoming.expiresAt > System.currentTimeMillis()) {
                    bucket.add(incoming)
                }
            }
            persistHost(host, bucket)
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        synchronized(lock) {
            val bucket = cache[url.host] ?: return emptyList()
            val now = System.currentTimeMillis()
            val expired = bucket.filter { it.expiresAt < now }
            if (expired.isNotEmpty()) {
                bucket.removeAll(expired.toSet())
                persistHost(url.host, bucket)
            }
            return bucket.filter { it.matches(url) }
        }
    }

    /**
     * Drop all saved cookies. Called on logout or when refresh fails.
     */
    fun clear() {
        synchronized(lock) {
            cache.clear()
            prefs.edit().clear().apply()
        }
    }

    fun hasRefreshToken(): Boolean {
        synchronized(lock) {
            val now = System.currentTimeMillis()
            return cache.values.any { bucket ->
                bucket.any { it.name == REFRESH_TOKEN && it.expiresAt > now }
            }
        }
    }

    /**
     * Token snapshot used to coalesce concurrent refresh attempts. If the access_token
     * cookie has changed since the snapshot was taken, another caller already refreshed
     * the session and we can skip our own /auth/refresh call.
     */
    fun accessTokenSnapshot(host: String): String? = synchronized(lock) {
        cache[host]?.firstOrNull { it.name == ACCESS_TOKEN }?.value
    }

    private fun loadAllFromDisk() {
        prefs.all.forEach { (host, value) ->
            val raw = value as? String ?: return@forEach
            runCatching {
                Json.decodeFromString(ListSerializer(StoredCookie.serializer()), raw)
            }.onSuccess { stored ->
                val now = System.currentTimeMillis()
                val live = stored
                    .filter { it.expiresAt > now }
                    .mapNotNull { it.toOkHttp() }
                if (live.isNotEmpty()) {
                    cache[host] = live.toMutableList()
                }
            }
        }
    }

    private fun persistHost(host: String, bucket: List<Cookie>) {
        if (bucket.isEmpty()) {
            prefs.edit().remove(host).apply()
            return
        }
        val payload = Json.encodeToString(
            ListSerializer(StoredCookie.serializer()),
            bucket.map(StoredCookie::fromOkHttp),
        )
        prefs.edit().putString(host, payload).apply()
    }

    @Serializable
    private data class StoredCookie(
        val name: String,
        val value: String,
        val expiresAt: Long,
        val domain: String,
        val path: String,
        val secure: Boolean,
        val httpOnly: Boolean,
        val hostOnly: Boolean,
    ) {
        fun toOkHttp(): Cookie? = runCatching {
            Cookie.Builder().apply {
                name(name)
                value(value)
                expiresAt(expiresAt)
                if (hostOnly) hostOnlyDomain(domain) else domain(domain)
                path(path)
                if (secure) secure()
                if (httpOnly) httpOnly()
            }.build()
        }.getOrNull()

        companion object {
            fun fromOkHttp(c: Cookie) = StoredCookie(
                name = c.name,
                value = c.value,
                expiresAt = c.expiresAt,
                domain = c.domain,
                path = c.path,
                secure = c.secure,
                httpOnly = c.httpOnly,
                hostOnly = c.hostOnly,
            )
        }
    }

    companion object {
        private const val PREFS_NAME = "cashflow_cookies"
        private const val REFRESH_TOKEN = "refresh_token"
        private const val ACCESS_TOKEN = "access_token"

        private fun createEncryptedPrefs(context: Context): SharedPreferences {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            return EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }
    }
}
