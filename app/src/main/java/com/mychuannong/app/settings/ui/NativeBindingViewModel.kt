package com.mychuannong.app.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mychuannong.app.school.*
import com.mychuannong.app.common.data.local.mmkv.StudentInfoManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal enum class BindingPhase { IDLE, LOADING, VERIFICATION, ERROR, IDENTITY_PENDING, CONFIRMED }
internal data class NativeBindingState(val phase:BindingPhase=BindingPhase.IDLE,val message:String="账号仅由手机连接学校认证，不发送到我的川农后台")
internal class NativeBindingViewModel:ViewModel() {
    // Memory only, never SavedStateHandle, preferences, logs or Intent extras.
    var number=""
    var password=""
    var admissionYearText=""
    private var session:NativeAuthSession?=null
    internal var challenge:AuthChallenge?=null;private set
    private var job:Job?=null
    private val mutable=MutableStateFlow(NativeBindingState())
    val state=mutable.asStateFlow()
    fun begin() {
        if(job?.isActive==true) return
        if(number.isBlank() || password.isBlank()) { mutable.value=NativeBindingState(BindingPhase.ERROR,"请完整填写学号和学校密码");return }
        if(admissionYearText.isNotBlank() && admissionYearText.toIntOrNull() !in 1900..2200) { mutable.value=NativeBindingState(BindingPhase.ERROR,"请填写四位入学年份，或留空");return }
        session=NativeAuthSession();fetchChallenge()
    }
    fun fetchChallenge() {
        if(job?.isActive==true) return
        mutable.value=NativeBindingState(BindingPhase.LOADING,"正在连接学校并获取实际验证码")
        job=viewModelScope.launch {
            try {
                challenge=requireNotNull(session).challenge()
                mutable.value=NativeBindingState(BindingPhase.VERIFICATION,"请完成学校图形验证码；无需再次输入账号密码")
            } catch(e:CancellationException) { throw e }
            catch(_:Exception) { mutable.value=NativeBindingState(BindingPhase.ERROR,"无法初始化学校认证。请检查网络后重试；这不代表密码错误") }
        }
    }
    fun submitVerification(code:String) {
        if(job?.isActive==true || code.isBlank()) return
        val currentChallenge=challenge ?: return
        val currentSession=session ?: return
        val entered=number.trim();val secret=password
        mutable.value=NativeBindingState(BindingPhase.LOADING,"正在提交学校认证并单独确认本人身份")
        job=viewModelScope.launch {
            try {
                val result=currentSession.login(entered,secret,code,currentChallenge)
                if(!SicauAuthProtocol.accepted(result)) {
                    challenge=null
                    mutable.value=NativeBindingState(BindingPhase.ERROR,SicauAuthProtocol.safeFailure(result));return@launch
                }
                // Verify the established server session independently; a 200 response/cookie is insufficient.
                val verified=try { currentSession.verify() } catch(e:CancellationException) { throw e } catch(_:Exception) { null }
                val id=verified?.let { SicauAuthProtocol.confirmedNumber(it,entered) }
                if(id==null) {
                    mutable.value=NativeBindingState(BindingPhase.IDENTITY_PENDING,"学校已接受认证，但会话复核或本人身份仍待确认，尚未标记绑定。教务业务回跳尚未核验")
                    return@launch
                }
                SicauNativeAuth.activate(currentSession,id)
                SchoolSyncEngine.sync(true)
                if(StudentInfoManager.hasCompleteBinding && StudentInfoManager.studentId==id) {
                    admissionYearText.toIntOrNull()?.let { StudentInfoManager.setAdmissionYearForVerifiedAccount(id,it) }
                    password="";challenge=null
                    mutable.value=NativeBindingState(BindingPhase.CONFIRMED,"川农认证身份已确认。已尝试同步；课表、成绩和考试解析仍待适配，教务业务会话回跳尚未核验")
                } else mutable.value=NativeBindingState(BindingPhase.IDENTITY_PENDING,"认证已完成，本机身份关联仍待确认")
            } catch(e:CancellationException) { throw e }
            catch(_:Exception) { mutable.value=NativeBindingState(BindingPhase.ERROR,"学校认证请求或本机会话保存失败，请重试；未将该异常判为密码错误") }
        }
    }
    fun cancel() { session?.cancel();job?.cancel();challenge=null;mutable.value=NativeBindingState(BindingPhase.IDLE,"已取消当前操作；此前已完成的账号关联与同步结果予以保留") }
    override fun onCleared() { cancel();password="";number="";super.onCleared() }
}
