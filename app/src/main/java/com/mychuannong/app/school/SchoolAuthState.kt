package com.mychuannong.app.school

enum class SchoolAuthPhase {
    UNBOUND, AUTH_REQUIRED, CAPTCHA_REQUIRED, AUTHENTICATING, AUTHENTICATED,
    SESSION_VALID, SESSION_EXPIRED, FETCHING, READY, ERROR,
}

enum class SchoolFailure(val userMessage: String) {
    NETWORK("网络连接失败，请检查网络后重试"),
    DNS("暂时无法连接学校，请稍后重试"),
    TLS("无法安全连接学校，请稍后重试"),
    TIMEOUT("学校响应超时，请重试"),
    CAPTCHA("验证码不正确或已过期，请刷新后重试"),
    CREDENTIALS("学号或密码未通过验证，请核对后重试"),
    EXPIRED("学校登录已过期，请重新认证"),
    REDIRECT("学校登录未完成，请重新认证"),
    PARSE("学校数据暂时无法读取，请稍后重试"),
    EMPTY("学校未返回数据，请稍后重试"),
    STORAGE("无法保存本机登录状态，请重试"),
    UNKNOWN("暂时未能完成操作，请重试");

    companion object {
        fun from(error: Throwable): SchoolFailure = when(error) {
            is java.net.UnknownHostException -> DNS
            is javax.net.ssl.SSLException -> TLS
            is java.net.SocketTimeoutException, is java.io.InterruptedIOException -> TIMEOUT
            is com.google.gson.JsonParseException, is IllegalArgumentException -> PARSE
            is java.io.IOException -> NETWORK
            else -> UNKNOWN
        }
    }
}

/** Academic authentication only; never infer this state from VPN or unified SSO. */
object SchoolAuthState {
    private val mutable = kotlinx.coroutines.flow.MutableStateFlow(SchoolAuthPhase.UNBOUND)
    val phase: kotlinx.coroutines.flow.StateFlow<SchoolAuthPhase> = mutable
    internal fun move(phase: SchoolAuthPhase) {
        // Online authentication never removes the verified owner of local learning data.
        // Only an explicit unbind may send a returning user through first-use binding.
        mutable.value = phase
    }
}

internal enum class UnifiedAuthPhase { UNKNOWN, RECOVERING, VALID, INTERACTION_REQUIRED }
internal object UnifiedAuthState {
    private val mutable = kotlinx.coroutines.flow.MutableStateFlow(UnifiedAuthPhase.UNKNOWN)
    val phase: kotlinx.coroutines.flow.StateFlow<UnifiedAuthPhase> = mutable
    fun move(next: UnifiedAuthPhase) { mutable.value = next }
}
