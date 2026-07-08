package com.kakeibo.android.di

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.kakeibo.android.BuildConfig
import com.kakeibo.android.core.network.api.AuthApiService
import com.kakeibo.android.core.network.api.HealthApi
import com.kakeibo.android.core.network.api.SyncApiService
import com.kakeibo.android.core.network.auth.PersistentCookieJar
import com.kakeibo.android.core.network.auth.RefreshAuthenticator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier @Retention(AnnotationRetention.BINARY)
annotation class RefreshOkHttpClient

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    /**
     * Bare client used by [RefreshAuthenticator] to call /auth/refresh. Has no
     * Authenticator attached so it cannot recurse, but shares the cookie jar so the
     * refreshed Set-Cookie persists.
     */
    @Provides
    @Singleton
    @RefreshOkHttpClient
    fun provideRefreshClient(cookieJar: PersistentCookieJar): OkHttpClient {
        val builder = OkHttpClient.Builder().cookieJar(cookieJar)
        if (BuildConfig.DEBUG) {
            builder.addInterceptor(
                HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
            )
        }
        return builder.build()
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        cookieJar: PersistentCookieJar,
        authenticator: RefreshAuthenticator,
    ): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .authenticator(authenticator)

        if (BuildConfig.DEBUG) {
            builder.addInterceptor(
                HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY }
            )
        }

        return builder.build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, json: Json): Retrofit {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL.trimEnd('/') + "/")
            .client(client)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }

    @Provides
    @Singleton
    fun provideHealthApi(retrofit: Retrofit): HealthApi =
        retrofit.create(HealthApi::class.java)

    @Provides
    @Singleton
    fun provideAuthApi(retrofit: Retrofit): AuthApiService =
        retrofit.create(AuthApiService::class.java)

    @Provides
    @Singleton
    fun provideSyncApi(retrofit: Retrofit): SyncApiService =
        retrofit.create(SyncApiService::class.java)
}
