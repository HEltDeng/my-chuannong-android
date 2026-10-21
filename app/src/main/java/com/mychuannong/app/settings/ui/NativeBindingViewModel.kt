package com.mychuannong.app.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mychuannong.app.school.*
import com.mychuannong.app.common.data.local.mmkv.StudentInfoManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.HttpUrl.Companion.toHttpUrl

internal enum class BindingPhase { IDLE, CHECKING_NETWORK, LOADING, VPN_BINDING, SCHOOL_VERIFICATION, ERROR, IDENTITY_PENDING, CONFIRMED }
internal data class NativeBindingState(
    val phase: BindingPhase = BindingPhase.IDLE,
    val message: String = "",
    val vpnBound: Boolean = false,
    val schoolVerificationAvailable: Boolean = false,
) {
    val busy get() = phase in setOf(BindingPhase.CHECKING_NETWORK, BindingPhase.LOADING, BindingPhase.VPN_BINDING, BindingPhase.SCHOOL_VERIFICATION)
    val canLogin get() = vpnBound
}
internal class NativeBindingViewModel(
    private val checkVpn: suspend () -> Boolean = {
        SicauNativeAuth.exportToWebView()
        SicauWebVpn.ensureAvailable()
    },
) : ViewModel() {
    // A successful explicit login enables local recovery by default. Never save unverified input.
    val rememberAcademic = true
    var number = StudentInfoManager.studentId
    var password = ""
    private var session: SicauJiaowuSession? = null
    private var job: Job? = null
    // Memory only. A browser Activity result never constitutes identity proof.
    private class Verification(val entered: String, val route: SicauJiaowuRoute, val revision: Long)
    private var verification: Verification? = null
    private val mutable = MutableStateFlow(NativeBindingState())
    val state = mutable.asStateFlow()

    private var prepared = false
    fun prepareAuthentication() {
        if (prepared || state.value.busy) return
        prepared = true
        if (SicauWebVpn.route() != null) {
            mutable.value = state.value.copy(phase = BindingPhase.CHECKING_NETWORK, message = "正在检查川农 e 认证…")
            job = viewModelScope.launch {
                val connected = verifyVpn()
                mutable.value = state.value.copy(phase = BindingPhase.IDLE, vpnBound = connected, message = "")
            }
        }
    }

    private suspend fun verifyVpn(): Boolean = try {
        withTimeoutOrNull(12_000) { checkVpn() } == true
    } catch (e: CancellationException) { throw e }
    catch (_: Exception) { false }

    fun startVpnBinding(): Boolean {
        if (state.value.busy) return false
        mutable.value = state.value.copy(phase = BindingPhase.VPN_BINDING, vpnBound = false, message = "")
        return true
    }

    fun begin() {
        if (job?.isActive == true || state.value.busy) return
        if (!state.value.canLogin) { mutable.value = state.value.copy(phase = BindingPhase.ERROR, message = "请先完成川农 e 认证"); return }
        if (number.isBlank()) { mutable.value = state.value.copy(phase = BindingPhase.ERROR, message = "请填写学号"); return }
        if (password.isBlank()) { mutable.value = state.value.copy(phase = BindingPhase.ERROR, message = "请输入教务密码"); return }
        val route = SicauWebVpn.route()
        if (route == null) { mutable.value = state.value.copy(phase = BindingPhase.ERROR, vpnBound = false, message = JiaowuFailure.WEBVPN_REQUIRED.userMessage); return }
        val entered = number.trim()
        val secret = password
        val remember = rememberAcademic
        val loginRevision = SicauNativeAuth.webRevision.value
        password = ""
        verification = null
        // A failed/cancelled optional relogin must preserve the existing local binding.
        mutable.value = state.value.copy(phase = BindingPhase.LOADING, schoolVerificationAvailable = false, message = "正在检查教务会话与本人身份")
        job = viewModelScope.launch {
            try {
                if (route.isWebVpn && (!verifyVpn() || route.prefix != SicauWebVpn.route()?.prefix)) {
                    mutable.value = state.value.copy(phase = BindingPhase.ERROR, vpnBound = false,
                        message = "校园 VPN 当前不可用，请重新绑定或检查网络后重试。")
                    return@launch
                }
                val academic = SicauJiaowuSession(SicauJiaowuCookieJar(route)).also { session = it }
                // Explicit binding always validates both entered credentials with the school.
                academic.login(entered, secret)
                completeBinding(academic, entered, loginRevision,
                    if (remember) AcademicCredential(entered, secret, route) else null)
            } catch (e: CancellationException) { throw e }
            catch (e: JiaowuException) {
                if (e.reason == JiaowuFailure.WEBVPN_REQUIRED) {
                    SicauWebVpn.move(WebVpnPhase.AUTH_REQUIRED)
                    mutable.value = state.value.copy(phase = BindingPhase.ERROR, vpnBound = false, message = e.reason.userMessage)
                } else {
                    SchoolAuthState.move(SchoolAuthPhase.ERROR)
                    if (e.reason.supportsSchoolVerification) verification = Verification(entered, route, loginRevision)
                    mutable.value = state.value.copy(phase = BindingPhase.ERROR, message = e.reason.userMessage,
                        schoolVerificationAvailable = verification != null)
                }
            } catch (_: Exception) { mutable.value = state.value.copy(phase = BindingPhase.ERROR, message = "学校连接或本机会话保存失败，原有缓存保留") }
            finally { password = "" }
        }
    }
    private suspend fun completeBinding(academic: SicauJiaowuSession, entered: String, revision: Long, credential: AcademicCredential? = null) {
        if (academic.jar.route.isWebVpn) SicauWebVpn.move(WebVpnPhase.AVAILABLE)
        SchoolAuthState.move(SchoolAuthPhase.FETCHING)
        session = null // The shared auth manager now owns it; closing this form must not cancel it.
        SchoolSyncEngine.bindJiaowu(academic, expectedRevision = revision, credential = credential)
        if (StudentInfoManager.hasCompleteBinding && StudentInfoManager.studentId == entered &&
            com.mychuannong.app.core.security.SchoolAccessGate.isAllowed) {
            val failures = SchoolSyncEngine.items.value.filterValues { it.phase !in setOf(SyncPhase.DATA, SyncPhase.EMPTY) }.keys
            val feedback = if (failures.isEmpty()) "教务本人身份已核验，课表、成绩、考试同步完成"
                else "教务本人身份已核验；部分数据未同步成功，请查看各页面提示后重试"
            verification = null
            mutable.value = state.value.copy(phase = BindingPhase.CONFIRMED, message = feedback, schoolVerificationAvailable = false)
        } else mutable.value = state.value.copy(phase = BindingPhase.IDENTITY_PENDING, message = "身份保存尚未完成，请重试")
    }
    fun startSchoolVerification(): SicauJiaowuRoute? {
        val request = verification ?: return null
        if (state.value.busy) return null
        if (request.entered != number.trim() || request.revision != SicauNativeAuth.webRevision.value ||
            request.route.isWebVpn && request.route.prefix != SicauWebVpn.route()?.prefix) {
            verification = null
            mutable.value = state.value.copy(phase = BindingPhase.ERROR, schoolVerificationAvailable = false,
                message = "账号或连接已变化，请重新登录教务账号")
            return null
        }
        password = ""
        mutable.value = state.value.copy(phase = BindingPhase.SCHOOL_VERIFICATION, message = "请在学校页面完成验证")
        return request.route
    }
    fun schoolVerificationFinished(checkIdentity: Boolean) {
        val request = verification
        if (state.value.phase != BindingPhase.SCHOOL_VERIFICATION || request == null) return
        if (!checkIdentity) {
            mutable.value = state.value.copy(phase = BindingPhase.ERROR, message = "学校验证尚未完成，可继续打开学校页面验证")
            return
        }
        mutable.value = state.value.copy(phase = BindingPhase.LOADING, message = "正在核对学校验证结果与本人学号")
        job = viewModelScope.launch {
            try {
                if (request.revision != SicauNativeAuth.webRevision.value || request.entered != number.trim() ||
                    request.route.isWebVpn && request.route.prefix != SicauWebVpn.route()?.prefix)
                    throw JiaowuException(JiaowuFailure.SESSION_UNAVAILABLE)
                val jar = SicauJiaowuCookieJar(request.route)
                if (!request.route.isWebVpn) {
                    val target = SicauJiaowuProtocol.PERSONAL_INFO
                    val cookies = withContext(Dispatchers.Main) {
                        if (androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.GET_COOKIE_INFO))
                            androidx.webkit.CookieManagerCompat.getCookieInfo(android.webkit.CookieManager.getInstance(), target)
                        else throw JiaowuException(JiaowuFailure.SESSION_UNAVAILABLE)
                    }
                    jar.acceptRawCookies(target.toHttpUrl(), cookies)
                }
                val academic = SicauJiaowuSession(jar).also { session = it }
                // A fresh protected profile, with the exact entered owner, is mandatory.
                // Do not resubmit the password or infer success from closing the website.
                academic.fetchPersonalInfo(request.entered)
                completeBinding(academic, request.entered, request.revision)
            } catch (e: CancellationException) { throw e }
            catch (e: JiaowuException) {
                mutable.value = state.value.copy(phase = BindingPhase.ERROR, schoolVerificationAvailable = true,
                    message = if (e.reason.requiresLogin || e.reason == JiaowuFailure.PERSONAL_INFO_PARSE_FAILED)
                        "学校验证尚未完成，请在学校页面完成登录与验证后再继续" else e.reason.userMessage)
            } catch (_: Exception) {
                mutable.value = state.value.copy(phase = BindingPhase.ERROR, schoolVerificationAvailable = true,
                    message = "学校验证结果暂时无法核对，请检查网络后重试")
            }
        }
    }
    fun vpnFinished(success: Boolean) {
        if (state.value.phase != BindingPhase.VPN_BINDING) return
        // SicauWebVpnActivity returns OK only after the protected school resource check.
        mutable.value = state.value.copy(phase = BindingPhase.IDLE, vpnBound = success, message = "")
    }
    fun cancel() { session?.cancel(); job?.cancel(); password = ""; verification = null; prepared = false; mutable.value = NativeBindingState() }
    override fun onCleared() { cancel(); number = ""; super.onCleared() }
}
