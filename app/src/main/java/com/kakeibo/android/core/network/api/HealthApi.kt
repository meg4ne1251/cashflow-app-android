package com.kakeibo.android.core.network.api

import retrofit2.http.GET

/**
 * Phase 0 smoke-test endpoint. Real API services land in Phase 1.
 */
interface HealthApi {
    @GET("/health")
    suspend fun health(): String
}
