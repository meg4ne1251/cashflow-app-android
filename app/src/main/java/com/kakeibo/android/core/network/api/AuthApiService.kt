package com.kakeibo.android.core.network.api

import com.kakeibo.android.core.network.dto.LoginRequest
import com.kakeibo.android.core.network.dto.LoginSuccessResponse
import com.kakeibo.android.core.network.dto.MeResponse
import com.kakeibo.android.core.network.dto.SetupRequest
import com.kakeibo.android.core.network.dto.SetupResponse
import com.kakeibo.android.core.network.dto.SetupStatusResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface AuthApiService {

    @GET("api/v1/auth/setup/status")
    suspend fun setupStatus(): SetupStatusResponse

    @POST("api/v1/auth/setup")
    suspend fun setup(@Body request: SetupRequest): SetupResponse

    @POST("api/v1/auth/login")
    suspend fun login(@Body request: LoginRequest): LoginSuccessResponse

    @POST("api/v1/auth/refresh")
    suspend fun refresh(): Response<Unit>

    @GET("api/v1/auth/me")
    suspend fun me(): MeResponse

    @POST("api/v1/auth/logout")
    suspend fun logout(): Response<Unit>
}
