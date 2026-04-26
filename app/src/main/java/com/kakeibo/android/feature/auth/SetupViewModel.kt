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

data class SetupUiState(
    val username: String = "",
    val password: String = "",
    val passwordConfirm: String = "",
    val usernameError: String? = null,
    val passwordError: String? = null,
    val confirmError: String? = null,
    val formError: String? = null,
    val isSubmitting: Boolean = false,
) {
    val canSubmit: Boolean
        get() = !isSubmitting &&
            username.isNotBlank() &&
            password.isNotBlank() &&
            passwordConfirm.isNotBlank()
}

@HiltViewModel
class SetupViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SetupUiState())
    val uiState: StateFlow<SetupUiState> = _uiState.asStateFlow()

    fun onUsernameChange(value: String) = _uiState.update {
        it.copy(username = value, usernameError = null, formError = null)
    }

    fun onPasswordChange(value: String) = _uiState.update {
        it.copy(password = value, passwordError = null, confirmError = null, formError = null)
    }

    fun onConfirmChange(value: String) = _uiState.update {
        it.copy(passwordConfirm = value, confirmError = null, formError = null)
    }

    fun submit() {
        val current = _uiState.value
        val usernameError = AuthRules.validateUsername(current.username)
        val passwordError = AuthRules.validateSetupPassword(current.password)
        val confirmError =
            AuthRules.validatePasswordConfirm(current.password, current.passwordConfirm)

        if (usernameError != null || passwordError != null || confirmError != null) {
            _uiState.update {
                it.copy(
                    usernameError = usernameError,
                    passwordError = passwordError,
                    confirmError = confirmError,
                )
            }
            return
        }

        _uiState.update { it.copy(isSubmitting = true, formError = null) }
        viewModelScope.launch {
            when (val setup = authRepository.setup(current.username, current.password)) {
                is AuthResult.Success -> {
                    // Backend doesn't auto-login on setup; immediately log in to mint cookies.
                    when (val login = authRepository.login(current.username, current.password)) {
                        is AuthResult.Success -> Unit
                        else -> _uiState.update {
                            it.copy(
                                isSubmitting = false,
                                formError = "セットアップは完了しましたがログインに失敗しました。再度ログインしてください",
                            )
                        }
                    }
                }
                is AuthResult.HttpError -> _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        formError = mapHttpError(setup.code),
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
                        formError = "セットアップに失敗しました",
                    )
                }
            }
        }
    }

    private fun mapHttpError(code: Int): String = when (code) {
        409 -> "既にセットアップ済みです。ログイン画面からサインインしてください"
        400 -> "入力内容を確認してください"
        in 500..599 -> "サーバーエラーが発生しました"
        else -> "セットアップに失敗しました ($code)"
    }
}
