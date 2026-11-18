package com.mychuannong.app.core.account

object AccountInputRules {
    private val mainlandMobile = Regex("^1[3-9]\\d{9}$")

    fun isValidPhone(value: String): Boolean = mainlandMobile.matches(value.trim())

    fun isValidPassword(value: String): Boolean = value.length in 8..200

    fun isConfirmedPassword(password: String, confirmation: String): Boolean =
        isValidPassword(password) && password.isNotBlank() && password == confirmation
    const val PASSWORD_REMINDER = "请妥善保管手机号和密码。需要账号帮助时，可通过“联系支持”核对身份。"

    fun canSubmit(
        phone: String,
        password: String,
        nickname: String,
        registering: Boolean,
        confirmation: String = "",
    ): Boolean = isValidPhone(phone) &&
        isValidPassword(password) &&
        (!registering || (nickname.trim().isNotEmpty() && isConfirmedPassword(password, confirmation)))
}

internal fun accountRestrictionMessage(
    accountStatus: String,
    expiresAtMillis: Long?,
    nowMillis: Long = System.currentTimeMillis(),
): String? {
    if (accountStatus !in setOf("LIMITED", "SUSPENDED")) return null
    if (expiresAtMillis == null) {
        return "你的账号已被限制访问，请联系管理员恢复使用"
    }
    val remainingMillis = (expiresAtMillis - nowMillis).coerceAtLeast(1L)
    val remainingDays = ((remainingMillis + DAY_MILLIS - 1) / DAY_MILLIS).coerceAtLeast(1L)
    return "你的账号已被限制访问，请在 $remainingDays 天后恢复使用"
}

private const val DAY_MILLIS = 24L * 60L * 60L * 1000L
