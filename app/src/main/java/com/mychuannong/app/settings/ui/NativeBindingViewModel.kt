package com.mychuannong.app.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mychuannong.app.school.*
import com.mychuannong.app.common.data.local.mmkv.StudentInfoManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal enum class BindingPhase { IDLE, CHECKING_NETWORK, LOADING, VPN_BINDING, ERROR, IDENTITY_PENDING, CONFIRMED }
internal enum class BindingNetwork { CAMPUS, OFF_CAMPUS }
internal data class NativeBindingState(
    val phase: BindingPhase = BindingPhase.IDLE,
    val message: String = "请选择当前网络环境，再继续绑定学号。",
    val network: BindingNetwork? = null,
    val vpnBound: Boolean = false,
) {
    val busy get() = phase in setOf(BindingPhase.CHECKING_NETWORK, BindingPhase.LOADING, BindingPhase.VPN_BINDING)
    val canLogin get() = network == BindingNetwork.CAMPUS || (network == BindingNetwork.OFF_CAMPUS && vpnBound)
}
internal class NativeBindingViewModel(
    private val checkVpn: suspend () -> Boolean = {
        SicauNativeAuth.exportToWebView()
        SicauWebVpn.check(force = true)
    },
) : ViewModel() {
    // Credentials are used once in memory; never saved or sent to another authentication service.
    var number = StudentInfoManager.studentId
    var password = ""
    private var session: SicauJiaowuSession? = null
    private var job: Job? = null
    private val mutable = MutableStateFlow(NativeBindingState())
    val state = mutable.asStateFlow()

    fun selectNetwork(network: BindingNetwork) {
        if (state.value.busy || state.value.network == network) return
        mutable.value = state.value.copy(network = network, phase = BindingPhase.IDLE, vpnBound = false,
            message = networkGuide(network))
        if (SicauWebVpn.route() != null) {
            mutable.value = state.value.copy(phase = BindingPhase.CHECKING_NETWORK, message = "正在检查已绑定的校园 VPN…")
            job = viewModelScope.launch {
                val connected = verifyVpn()
                mutable.value = state.value.copy(phase = BindingPhase.IDLE, vpnBound = connected,
                    message = if (connected) CampusLoginCopy.vpnBound else networkGuide(network))
            }
        }
    }

    private fun networkGuide(network: BindingNetwork) = if (network == BindingNetwork.CAMPUS)
        "推荐绑定校园 VPN，校内校外均可使用；也可使用校园网直接登录教务系统。"
        else "请先绑定校园 VPN，再登录教务账号。校内、校外均推荐使用此通道。"

    private suspend fun verifyVpn(): Boolean = try {
        withTimeoutOrNull(12_000) { checkVpn() } == true
    } catch (e: CancellationException) { throw e }
    catch (_: Exception) { false }

    fun startVpnBinding(): Boolean {
        if (state.value.network == null || state.value.busy) return false
        mutable.value = state.value.copy(phase = BindingPhase.VPN_BINDING, vpnBound = false, message = "请完成校园 VPN 绑定。")
        return true
    }

    fun begin() {
        if (job?.isActive == true || state.value.busy) return
        if (state.value.network == null) { mutable.value = state.value.copy(phase = BindingPhase.ERROR, message = "请先选择当前网络环境"); return }
        if (!state.value.canLogin) { mutable.value = state.value.copy(phase = BindingPhase.ERROR, message = "请先完成校园 VPN 绑定，再继续登录"); return }
        if (number.isBlank()) { mutable.value = state.value.copy(phase = BindingPhase.ERROR, message = "请填写学号"); return }
        val route = AcademicRoutePreference.select(state.value.network == BindingNetwork.CAMPUS,
            state.value.vpnBound, SicauWebVpn.route())
        if (route == null) { mutable.value = state.value.copy(phase = BindingPhase.ERROR, vpnBound = false, message = JiaowuFailure.WEBVPN_REQUIRED.userMessage); return }
        val entered = number.trim()
        val secret = password
        password = ""
        mutable.value = state.value.copy(phase = BindingPhase.LOADING, message = "正在检查教务会话与本人身份")
        job = viewModelScope.launch {
            try {
                if (route.isWebVpn && (!verifyVpn() || route.prefix != SicauWebVpn.route()?.prefix)) {
                    mutable.value = state.value.copy(phase = BindingPhase.ERROR, vpnBound = false,
                        message = "校园 VPN 当前不可用，请重新绑定或检查网络后重试。")
                    return@launch
                }
                val academic = SicauJiaowuSession(SicauJiaowuCookieJar(route)).also { session = it }
                // WebView may already hold an academic session. Verify it natively before asking
                // for a password. A mismatched identity is terminal, never an automatic retry.
                val restored = if (route.isWebVpn) try {
                    academic.fetchPersonalInfo(entered); true
                } catch (e: JiaowuException) {
                    if (e.reason !in setOf(JiaowuFailure.SESSION_EXPIRED, JiaowuFailure.SESSION_COOKIE_MISSING,
                            JiaowuFailure.PERSONAL_INFO_PARSE_FAILED)) throw e
                    false
                } else false
                if (!restored) {
                    if (secret.isBlank()) {
                        mutable.value = state.value.copy(phase = BindingPhase.ERROR, message = "教务需要独立登录，请填写教务密码（不是统一认证密码）")
                        return@launch
                    }
                    academic.login(entered, secret)
                }
                if (route.isWebVpn) SicauWebVpn.move(WebVpnPhase.AVAILABLE)
                SchoolAuthState.move(SchoolAuthPhase.FETCHING)
                SchoolSyncEngine.bindJiaowu(academic)
                if (StudentInfoManager.hasCompleteBinding && StudentInfoManager.studentId == entered) {
                    val results = SchoolSyncEngine.items.value
                    val failures = results.filterValues { it.phase !in setOf(SyncPhase.DATA, SyncPhase.EMPTY) }.keys
                    val feedback = if (failures.isEmpty()) "教务本人身份已核验，课表、成绩、考试同步完成"
                        else "教务本人身份已核验；部分数据未同步成功，请查看各页面提示后重试"
                    mutable.value = state.value.copy(phase = BindingPhase.CONFIRMED, message = feedback)
                } else mutable.value = state.value.copy(phase = BindingPhase.IDENTITY_PENDING, message = "身份保存尚未完成，请重试")
            } catch (e: CancellationException) { throw e }
            catch (e: JiaowuException) {
                if (e.reason == JiaowuFailure.WEBVPN_REQUIRED) {
                    SicauWebVpn.move(WebVpnPhase.AUTH_REQUIRED)
                    mutable.value = state.value.copy(phase = BindingPhase.ERROR, vpnBound = false, message = e.reason.userMessage)
                } else {
                    SchoolAuthState.move(SchoolAuthPhase.ERROR)
                    mutable.value = state.value.copy(phase = BindingPhase.ERROR, message = e.reason.userMessage)
                }
            } catch (_: Exception) { mutable.value = state.value.copy(phase = BindingPhase.ERROR, message = "学校连接或本机会话保存失败，原有缓存保留") }
            finally { password = "" }
        }
    }
    fun vpnFinished(success: Boolean) {
        if (state.value.network == null || state.value.phase != BindingPhase.VPN_BINDING) return
        mutable.value = state.value.copy(phase = BindingPhase.IDLE, vpnBound = success,
            message = if (success) CampusLoginCopy.vpnBound else if (state.value.network == BindingNetwork.CAMPUS)
                "尚未绑定校园 VPN，可重新绑定，或使用校园网直接登录教务系统。"
                else "尚未绑定校园 VPN，请完成绑定后继续登录。")
    }
    fun cancel() { session?.cancel(); job?.cancel(); password = ""; mutable.value = NativeBindingState() }
    override fun onCleared() { cancel(); number = ""; super.onCleared() }
}
