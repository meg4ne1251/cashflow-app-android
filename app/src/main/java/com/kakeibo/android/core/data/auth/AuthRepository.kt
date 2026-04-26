package com.kakeibo.android.core.data.auth

import com.kakeibo.android.core.network.api.AuthApiService
import com.kakeibo.android.core.network.dto.LoginRequest
import com.kakeibo.android.core.network.dto.SetupRequest
import retrofit2.HttpException
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val api: AuthApiService,
    private val sessionManager: SessionManager,
) {

    /**
     * Probes /me to see if the existing cookie still authorises us. The OkHttp
     * Authenticator will transparently refresh if access_token is expired.
     */
    suspend fun resumeSession(): AuthResult<String> = call {
        val me = api.me()
        sessionManager.setAuthenticated(me.username)
        me.username
    }

    suspend fun fetchSetupStatus(): AuthResult<Boolean> = call {
        api.setupStatus().needs_setup
    }

    suspend fun login(username: String, password: String): AuthResult<String> = call {
        val response = api.login(LoginRequest(username = username, password = password))
        sessionManager.setAuthenticated(response.username)
        response.username
    }

    suspend fun setup(username: String, password: String): AuthResult<String> = call {
        val response = api.setup(SetupRequest(username = username, password = password))
        // The setup endpoint does not log the user in. Caller chains login() next.
        response.user.username
    }

    suspend fun logout(): AuthResult<Unit> = call {
        runCatching { api.logout() }
            .onFailure { Timber.w(it, "Logout call failed; clearing local session anyway") }
        sessionManager.setLoggedOut()
    }

    private inline fun <T> call(block: () -> T): AuthResult<T> = try {
        AuthResult.Success(block())
    } catch (e: HttpException) {
        AuthResult.HttpError(e.code(), e.message())
    } catch (e: IOException) {
        AuthResult.NetworkError(e.message ?: "network")
    } catch (e: Exception) {
        AuthResult.Unknown(e.message ?: e::class.simpleName.orEmpty())
    }
}

sealed interface AuthResult<out T> {
    data class Success<T>(val value: T) : AuthResult<T>
    data class HttpError(val code: Int, val message: String) : AuthResult<Nothing>
    data class NetworkError(val message: String) : AuthResult<Nothing>
    data class Unknown(val message: String) : AuthResult<Nothing>
}
