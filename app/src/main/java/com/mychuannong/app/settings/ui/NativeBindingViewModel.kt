package com.mychuannong.app.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mychuannong.app.school.*
import com.mychuannong.app.common.data.local.mmkv.StudentInfoManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal enum class BindingPhase { IDLE, LOADING, VERIFICATION, ERROR, IDENTITY_PENDING, CONFIRMED }
internal data class NativeBindingState(val phase:BindingPhase=BindingPhase.IDLE,val message:String="")
internal class NativeBindingViewModel:ViewModel() {
    // Memory only, never SavedStateHandle, preferences, logs or Intent extras.
    var number=StudentInfoManager.studentId
    var password=""
    private var session:NativeAuthSession?=null
    private var jiaowuSession:SicauJiaowuSession?=null
    internal var challenge:AuthChallenge?=null;private set
    private var job:Job?=null
    private val mutable=MutableStateFlow(NativeBindingState(message=when {
        StudentInfoManager.hasCompleteBinding -> "已绑定四川农业大学学校账号"
        number.isNotBlank() -> "学校登录已过期，请重新登录"
        else -> ""
    }))
    val state=mutable.asStateFlow()
    fun begin() {
        if(job?.isActive==true) return
        if(number.isBlank() || password.isBlank()) { mutable.value=NativeBindingState(BindingPhase.ERROR,"请完整填写学号和学校密码");return }
        session=NativeAuthSession();fetchChallenge()
    }
    fun fetchChallenge() {
        if(job?.isActive==true) return
        mutable.value=NativeBindingState(BindingPhase.LOADING,"正在获取验证码")
        job=viewModelScope.launch {
            try {
                challenge=requireNotNull(session).challenge()
                SchoolAuthState.move(SchoolAuthPhase.CAPTCHA_REQUIRED)
                mutable.value=NativeBindingState(BindingPhase.VERIFICATION,"请输入验证码")
            } catch(e:CancellationException) { throw e }
            catch(_:Exception) { mutable.value=NativeBindingState(BindingPhase.ERROR,"无法连接学校，请检查网络后重试") }
        }
    }
    fun submitVerification(code:String) {
        if(job?.isActive==true || code.isBlank()) return
        val currentChallenge=challenge ?: return
        val currentSession=session ?: return
        challenge=null // Each challenge can be submitted only once.
        val entered=number.trim();val secret=password
        SchoolAuthState.move(SchoolAuthPhase.AUTHENTICATING)
        mutable.value=NativeBindingState(BindingPhase.LOADING,"正在登录")
        job=viewModelScope.launch {
            try {
                val result=currentSession.login(entered,secret,code,currentChallenge)
                if(!SicauAuthProtocol.accepted(result)) {
                    challenge=null
                    mutable.value=NativeBindingState(BindingPhase.ERROR,SicauAuthProtocol.safeFailure(result));return@launch
                }
                // Unified auth is retained for existing portal features. Only the authenticated
                // jiaowu personal profile can establish the binding owner.
                val academic=SicauJiaowuSession().also { jiaowuSession=it }
                val id=academic.login(entered,secret).studentId
                SchoolAuthState.move(SchoolAuthPhase.AUTHENTICATED)
                SchoolAuthState.move(SchoolAuthPhase.FETCHING)
                SchoolSyncEngine.bindJiaowu(academic,currentSession)
                if(StudentInfoManager.hasCompleteBinding && StudentInfoManager.studentId==id &&
                    SchoolAuthState.phase.value in setOf(SchoolAuthPhase.SESSION_VALID,SchoolAuthPhase.READY)) {
                    password="";challenge=null
                    val feedback="学校账号已绑定"
                    mutable.value=NativeBindingState(BindingPhase.CONFIRMED,feedback)
                } else mutable.value=NativeBindingState(BindingPhase.IDENTITY_PENDING,"登录状态保存未完成，请重试")
            } catch(e:CancellationException) { throw e }
            catch(e:JiaowuException) {
                SchoolAuthState.move(if(e.reason==JiaowuFailure.SESSION_EXPIRED) SchoolAuthPhase.SESSION_EXPIRED else SchoolAuthPhase.ERROR)
                mutable.value=NativeBindingState(BindingPhase.ERROR,e.reason.userMessage)
            }
            catch(e:Exception) { mutable.value=NativeBindingState(BindingPhase.ERROR,SchoolFailure.from(e).userMessage) }
        }
    }
    fun cancel() { session?.cancel();jiaowuSession?.cancel();job?.cancel();challenge=null;mutable.value=NativeBindingState(BindingPhase.IDLE,"") }
    override fun onCleared() { cancel();password="";number="";super.onCleared() }
}
