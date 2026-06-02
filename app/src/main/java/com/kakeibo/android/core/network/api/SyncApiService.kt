package com.kakeibo.android.core.network.api

import com.kakeibo.android.core.network.dto.SyncPullResponse
import com.kakeibo.android.core.network.dto.SyncPushRequest
import com.kakeibo.android.core.network.dto.SyncPushResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface SyncApiService {

    /**
     * Pulls all rows updated strictly after [since] (ISO-8601 OffsetDateTime), capped per
     * entity by the server. [SyncPullResponse.has_more] signals that another page should be
     * fetched with an advanced cursor.
     */
    @GET("api/v1/sync/pull")
    suspend fun pull(@Query("since") since: String): SyncPullResponse

    @POST("api/v1/sync/push")
    suspend fun push(@Body request: SyncPushRequest): SyncPushResponse
}
