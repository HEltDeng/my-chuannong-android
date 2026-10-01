package com.mychuannong.app.common.redux.store

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.concurrent.atomic.AtomicLong
import com.mychuannong.app.common.data.local.mmkv.StudentInfoManager
import com.mychuannong.app.common.data.local.mmkv.UserInfoManager
import com.mychuannong.app.common.redux.action.UserAction
import com.mychuannong.app.common.redux.state.UserState
import com.mychuannong.app.core.PlanetApplication
import com.mychuannong.app.core.Store
import com.mychuannong.app.core.security.SchoolSessionCoordinator
import com.mychuannong.app.core.security.SchoolSessionResult
import com.mychuannong.app.core.security.SchoolSessionState
import com.mychuannong.app.utils.event.AppEventBus
import com.mychuannong.app.utils.event.FinishEvent
import com.mychuannong.app.widget.dialog.BindingFromWebDialog
import com.mychuannong.app.widget.dialog.NormalResponseDialog
import com.mychuannong.app.school.core.RetrofitUtils as CsustRetrofitUtils
import com.mychuannong.app.school.education.data.remote.services.AuthService
import com.mychuannong.app.school.mooc.data.remote.repository.MoocRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class UserStore : Store<UserState, UserAction>() {
    companion object {
        private const val TAG = "UserStore"
        private const val NO_ACTIVE_BINDING = 0L
        private var currentState = UserState()
        private val activeBindingRequestId = AtomicLong(NO_ACTIVE_BINDING)
    }

    private val handler = Handler(Looper.getMainLooper())

    override fun handleEvent(action: UserAction) {
        currentState = when (action) {
            is UserAction.initilaize -> {
                _state.onNext(currentState)
                currentState
            }

            is UserAction.QueryIsLastedApk -> {
                // 兼容保留旧 action，但彻底阻断原“上游”版本接口及旧兜底接口。
                // 新版本检查只由 AppUpdateChecker 访问“我的川农”后端。
                Log.i(TAG, "忽略旧版本检查 action，使用我的川农本地版本服务")
                currentState
            }

            is UserAction.BindingStudentNumber -> {
                if (!activeBindingRequestId.compareAndSet(
                        NO_ACTIVE_BINDING,
                        action.requestId
                    )
                ) {
                    currentState.uiForLoading = false
                    postBindingDialog(action.context) { activity ->
                        NormalResponseDialog(
                            activity,
                            "已有绑定请求正在处理中，请稍候。",
                            "请稍候"
                        ).show()
                    }
                    _state.onNext(currentState)
                    return
                }
                currentState.uiForLoading = true
                SchoolSessionState.explicitBindingInFlight.set(true)
                SchoolSessionState.invalidate()
                CoroutineScope(Dispatchers.IO).launch {
                    var bindingCompleted = false
                    try {
                        SchoolSessionCoordinator.instance.withExclusiveSessionChange {
                            CsustRetrofitUtils.ClearClient("moocClient")
                            CsustRetrofitUtils.ClearClient("EducationClient")
                            try {
                                val ssoResult = MoocRepository.instance
                                    .login(action.studentNumber, action.studentPassword)
                                    .filter { it !is com.mychuannong.app.school.core.Resource.Loading }
                                    .first()
                                when (ssoResult) {
                                    is com.mychuannong.app.school.core.Resource.Success -> {
                                        Log.d(TAG, "SSO 验证成功")
                                        var pendingAvatarUrl = ""
                                        var pendingUserName = ""
                                        runCatching {
                                            MoocRepository.instance
                                                .getLoginUser()
                                                .filter {
                                                    it !is com.mychuannong.app.school.core.Resource.Loading
                                                }
                                                .first()
                                        }.onSuccess { userResource ->
                                            if (userResource is com.mychuannong.app.school.core.Resource.Success) {
                                                userResource.data?.let { sso ->
                                                    pendingAvatarUrl = sso.avatar
                                                    pendingUserName = sso.userName
                                                }
                                            }
                                        }.onFailure {
                                            Log.w(TAG, "SSO 用户资料暂时不可用")
                                        }

                                        val eduSuccess = AuthService.login(
                                            action.studentNumber,
                                            action.studentPassword
                                        )
                                        currentState.uiForLoading = false
                                        if (eduSuccess) {
                                            Log.d(TAG, "教务验证成功")
                                            val credentialsSaved = StudentInfoManager.saveCredentials(
                                                action.studentNumber,
                                                action.studentPassword
                                            )
                                            if (credentialsSaved) {
                                                pendingAvatarUrl.takeIf(String::isNotBlank)?.let {
                                                    UserInfoManager.userAvatar = it
                                                    currentState.avatarUri = it
                                                    currentState.userProfile.avatarUrl = it
                                                }
                                                pendingUserName.takeIf(String::isNotBlank)?.let {
                                                    UserInfoManager.account = it
                                                }
                                                currentState.userStats = currentState.userStats.copy(
                                                    studentNumber = action.studentNumber
                                                )
                                                SchoolSessionState.markReady()
                                                bindingCompleted = true
                                            } else {
                                                clearTransientSchoolSession()
                                                postBindingDialog(action.context) { activity ->
                                                    NormalResponseDialog(
                                                        activity,
                                                        "凭据已验证，但无法安全保存到本机，请重试。",
                                                        "保存失败"
                                                    ).show()
                                                }
                                            }
                                        } else {
                                            clearTransientSchoolSession()
                                            postBindingDialog(action.context) { activity ->
                                                NormalResponseDialog(
                                                    activity,
                                                    "教务验证未完成，请检查账号信息或稍后重试。",
                                                    "绑定失败"
                                                ).show()
                                            }
                                        }
                                        _state.onNext(currentState)
                                    }

                                    is com.mychuannong.app.school.core.Resource.Error -> {
                                        currentState.uiForLoading = false
                                        clearTransientSchoolSession()
                                        if (ssoResult.msg.contains("请在手机网页登录一次")) {
                                            postBindingDialog(action.context) { activity ->
                                                BindingFromWebDialog(
                                                    activity,
                                                    "学校统一身份认证要求先完成一次网页登录。",
                                                    "绑定失败",
                                                    action.webLogin
                                                ).show()
                                            }
                                        } else {
                                            postBindingDialog(action.context) { activity ->
                                                NormalResponseDialog(
                                                    activity,
                                                    "身份验证未完成，请检查账号信息或网络后重试。",
                                                    "绑定失败"
                                                ).show()
                                            }
                                        }
                                        _state.onNext(currentState)
                                    }

                                    else -> {
                                        currentState.uiForLoading = false
                                        clearTransientSchoolSession()
                                        postBindingDialog(action.context) { activity ->
                                            NormalResponseDialog(
                                                activity,
                                                "网络暂时不可用，请稍后重试。",
                                                "绑定失败"
                                            ).show()
                                        }
                                        _state.onNext(currentState)
                                    }
                                }
                            } catch (_: Exception) {
                                currentState.uiForLoading = false
                                clearTransientSchoolSession()
                                postBindingDialog(action.context) { activity ->
                                    NormalResponseDialog(
                                        activity,
                                        "网络暂时不可用，请稍后重试。",
                                        "绑定失败"
                                    ).show()
                                }
                                _state.onNext(currentState)
                            }
                        }
                    } finally {
                        if (activeBindingRequestId.compareAndSet(
                                action.requestId,
                                NO_ACTIVE_BINDING
                            )
                        ) {
                            SchoolSessionState.explicitBindingInFlight.set(false)
                        }
                    }
                    if (bindingCompleted) {
                        handler.post {
                            AppEventBus.finishEvent.tryEmit(
                                FinishEvent("bindingUser", action.requestId)
                            )
                        }
                        PlanetApplication.clearSchoolDataCacheAll()
                    }
                }
                _state.onNext(currentState)
                currentState
            }

            is UserAction.WebLoginSuccess -> {
                // Persist only after BindingStudentNumber completes the existing SSO and
                // education-system validation; the web callback alone is not a binding.
                handleEvent(
                    UserAction.BindingStudentNumber(
                        action.context,
                        action.requestId,
                        action.account,
                        action.password
                    ) {}
                )
                currentState
            }

            is UserAction.RefreshMoocProfileSilently -> {
                val alreadyHasProfile = UserInfoManager.account.isNotBlank()
                val canRefresh = StudentInfoManager.hasCompleteBinding &&
                    (!SchoolSessionState.isReady() || !alreadyHasProfile)
                if (canRefresh && SchoolSessionState.bootstrapInFlight.compareAndSet(false, true)) {
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            when (SchoolSessionCoordinator.instance.ensureReady()) {
                                SchoolSessionResult.Ready -> Unit
                                SchoolSessionResult.MissingCredentials,
                                SchoolSessionResult.TemporarilyUnavailable -> return@launch
                            }

                            if (alreadyHasProfile) return@launch
                            val userResource = MoocRepository.instance
                                .getLoginUser()
                                .filter { it !is com.mychuannong.app.school.core.Resource.Loading }
                                .first()
                            if (userResource !is com.mychuannong.app.school.core.Resource.Success) {
                                return@launch
                            }

                            userResource.data?.let { sso ->
                                if (sso.avatar.isNotBlank()) {
                                    UserInfoManager.userAvatar = sso.avatar
                                }
                                if (sso.userName.isNotBlank()) {
                                    UserInfoManager.account = sso.userName
                                }
                            }
                        } catch (_: Throwable) {
                            Log.w(TAG, "静默刷新 MOOC 会话失败")
                        } finally {
                            SchoolSessionState.bootstrapInFlight.set(false)
                        }
                    }
                }
                currentState
            }
        }
    }

    private suspend fun clearTransientSchoolSession() {
        SchoolSessionState.invalidate()
        CsustRetrofitUtils.ClearClient("moocClient")
        CsustRetrofitUtils.ClearClient("EducationClient")
    }

    private fun postBindingDialog(context: Context, show: (Activity) -> Unit) {
        handler.post {
            val activity = context as? Activity ?: return@post
            if (activity.isFinishing || activity.isDestroyed) return@post
            show(activity)
        }
    }

    fun getUserState(): UserState = currentState
}
