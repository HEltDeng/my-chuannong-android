package com.mychuannong.app.school

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class SchoolState { UNBOUND, LOGGING_IN, IDENTITY_UNVERIFIED, READY, EXPIRED, PARSER_PENDING, NETWORK_ERROR }
data class SchoolSyncStatus(val state: SchoolState, val message: String, val updatedAt: Long? = null)
/** School-owned page structure has not been supplied. A missing parser is never an empty success. */
object SicauAdapter {
    const val ACADEMIC = "https://jiaowu.sicau.edu.cn/"
    // Use the school's registered portal service; bare /wui/ has no business destination.
    // `choose` is the official existing-session flow, falling back to the school login form.
    const val AUTH = "https://auth.sicau.edu.cn/wui/#/?appid=ywtb&service=https%3A%2F%2Fywtb.sicau.edu.cn%2Fcas%2Flogin%2Fuser&cascallback=choose"
    const val PENDING = "学校数据暂时无法读取，请稍后重试"
    private val mutableStatus = MutableStateFlow(SchoolSyncStatus(SchoolState.UNBOUND, "尚未连接学校账号"))
    val status = mutableStatus.asStateFlow()
    fun opening() { mutableStatus.value = SchoolSyncStatus(SchoolState.LOGGING_IN, "请在学校页面自行登录") }
    fun confirmed() { mutableStatus.value = SchoolSyncStatus(SchoolState.READY, "教务本人身份已确认；各业务同步状态独立显示") }
    fun expired() { mutableStatus.value = SchoolSyncStatus(SchoolState.EXPIRED, "学校教务登录已过期，请重新认证") }
    fun pageLoaded() { mutableStatus.value = SchoolSyncStatus(SchoolState.IDENTITY_UNVERIFIED, "页面已打开；学校身份尚未确认，不会标记已绑定") }
    fun networkError() { mutableStatus.value = SchoolSyncStatus(SchoolState.NETWORK_ERROR, "学校网页连接失败，可重试；当前网络可能无法访问") }
    fun pending(): String { mutableStatus.value = SchoolSyncStatus(SchoolState.PARSER_PENDING, PENDING); return PENDING }
    fun clear() { mutableStatus.value = SchoolSyncStatus(SchoolState.UNBOUND, "尚未连接学校账号") }
}
