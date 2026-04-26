package com.kakeibo.android.feature.auth

/**
 * Local validation rules mirror Web's Zod schema (web/src/validation/schemas.ts):
 *  - username: 3-50 chars, alphanumeric / _ / - / .
 *  - password (login): non-empty
 *  - password (setup): >= 8 chars
 */
internal object AuthRules {
    private val USERNAME = Regex("^[A-Za-z0-9._-]{3,50}$")

    fun validateUsername(value: String): String? = when {
        value.isBlank() -> "ユーザー名を入力してください"
        !USERNAME.matches(value) -> "3〜50 文字の英数字 / . _ - で入力してください"
        else -> null
    }

    fun validateLoginPassword(value: String): String? =
        if (value.isBlank()) "パスワードを入力してください" else null

    fun validateSetupPassword(value: String): String? = when {
        value.isBlank() -> "パスワードを入力してください"
        value.length < 8 -> "8 文字以上で入力してください"
        else -> null
    }

    fun validatePasswordConfirm(password: String, confirm: String): String? = when {
        confirm.isBlank() -> "確認用パスワードを入力してください"
        password != confirm -> "パスワードが一致しません"
        else -> null
    }
}
