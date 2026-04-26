package com.kakeibo.android.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kakeibo.android.core.data.auth.AuthRepository
import com.kakeibo.android.core.data.auth.AuthResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val username: String = "",
    val password: String = "",
    val usernameError: String? = null,
    val passwordError: String? = null,
    val formError: String? = null,
    val isSubmitting: Boolean = false,
) {
    val canSubmit: Boolean
        get() = !isSubmitting && username.isNotBlank() && password.isNotBlank()
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onUsernameChange(value: String) = _uiState.update {
        it.copy(username = value, usernameError = null, formError = null)
    }

    fun onPasswordChange(value: String) = _uiState.update {
        it.copy(password = value, passwordError = null, formError = null)
    }

    fun submit() {
        val current = _uiState.value
        val usernameError = AuthRules.validateUsername(current.username)
        val passwordError = AuthRules.validateLoginPassword(current.password)
        if (usernameError != null || passwordError != null) {
            _uiState.update {
                it.copy(usernameError = usernameError, passwordError = passwordError)
            }
            return
        }

        _uiState.update { it.copy(isSubmitting = true, formError = null) }
        viewModelScope.launch {
            when (val result = authRepository.login(current.username, current.password)) {
                is AuthResult.Success -> {
                    // SessionManager flips to Authenticated; root navigator handles the swap.
                }
                is AuthResult.HttpError -> _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        formError = mapHttpError(result.code),
                    )
                }
                is AuthResult.NetworkError -> _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        formError = "ネットワークに接続できません",
                    )
                }
                is AuthResult.Unknown -> _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        formError = "ログインに失敗しました",
                    )
                }
            }
        }
    }

    private fun mapHttpError(code: Int): String = when (code) {
        401 -> "ユーザー名またはパスワードが違います"
        429 -> "試行回数が多すぎます。しばらく待ってから再度お試しください"
        in 500..599 -> "サーバーエラーが発生しました"
        else -> "ログインに失敗しました ($code)"
    }
}
