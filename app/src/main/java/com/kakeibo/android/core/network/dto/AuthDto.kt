package com.kakeibo.android.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class SetupStatusResponse(val needs_setup: Boolean)

@Serializable
data class SetupRequest(val username: String, val password: String)

@Serializable
data class UserDto(val id: String, val username: String)

@Serializable
data class SetupResponse(val user: UserDto)

@Serializable
data class LoginRequest(val username: String, val password: String)

@Serializable
data class LoginSuccessResponse(val username: String)

@Serializable
data class MeResponse(val username: String)

@Serializable
data class PasswordChangeRequest(
    val current_password: String,
    val new_password: String,
)
