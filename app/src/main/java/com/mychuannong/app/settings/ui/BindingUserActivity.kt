package com.mychuannong.app.settings.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.mychuannong.app.base.ComposeActivity
import com.mychuannong.app.common.data.local.mmkv.StudentInfoManager
import com.mychuannong.app.core.Route
import com.mychuannong.app.core.privacy.AccountDataManager
import com.mychuannong.app.core.security.SchoolAccessGate
import com.mychuannong.app.core.theme.AppTheme
import com.mychuannong.app.core.theme.SecondaryScaffoldHeader
import com.mychuannong.app.school.CampusNetworkStatus
import com.mychuannong.app.school.CampusNetworkViewModel
import com.mychuannong.app.school.SicauWebVpn
import com.mychuannong.app.school.UnifiedAcademicEntryActivity
import com.mychuannong.app.school.UnifiedSchoolAuthenticationActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** One school identity. The academic profile is accepted only after official SSO proof. */
class BindingUserActivity : ComposeActivity() {
    companion object {
        const val EXTRA_IS_SWITCH_ACCOUNT = "extra_is_switch_account"
        const val EXTRA_REAUTHENTICATE = "extra_reauthenticate"
        const val EXTRA_REQUIRED = "extra_required_binding"
    }

    private val network: CampusNetworkViewModel by viewModels()
    private var bound by mutableStateOf(false)
    private var busy by mutableStateOf(false)
    private var error by mutableStateOf<String?>(null)
    private var confirmUnbind by mutableStateOf(false)
    private var resumeRequest = false
    private var waitingForChild = false
    private var directEntry = false
    private var completed = false
    private val authentication = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (completed || isFinishing) return@registerForActivityResult
        waitingForChild = false
        busy = false
        bound = SchoolAccessGate.isAllowed && StudentInfoManager.hasCompleteBinding
        if (result.resultCode == RESULT_OK && bound) {
            setResult(RESULT_OK)
            if (resumeRequest || directEntry) {
                completed = true
                if (!resumeRequest) SchoolAccessGate.resumeLearningPage(this)
                // The destination owns its learning-data loading. Binding success
                // never waits for courses, grades or exams to finish downloading.
                finish()
            }
        } else if (resumeRequest || directEntry) finish()
    }
    private val silentAcademic = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        waitingForChild = false
        if (result.resultCode == RESULT_OK && SchoolAccessGate.isAllowed) {
            busy = false; setResult(RESULT_OK); finish()
        } else if (result.resultCode == RESULT_FIRST_USER) {
            busy = false; setResult(RESULT_FIRST_USER, result.data); finish()
        } else if (result.resultCode == UnifiedAcademicEntryActivity.RESULT_VERIFICATION_REQUIRED) launchAuthentication()
        else finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!SchoolAccessGate.enforce(this)) return
        bound = SchoolAccessGate.isAllowed && StudentInfoManager.hasCompleteBinding
        resumeRequest = intent.getBooleanExtra(EXTRA_REAUTHENTICATE, false) && bound
        directEntry = savedInstanceState?.getBoolean("direct_school_entry")
            ?: (!bound || intent.getBooleanExtra(EXTRA_REQUIRED, false))
        waitingForChild = savedInstanceState?.getBoolean("waiting_for_authentication") == true
        completed = savedInstanceState?.getBoolean("school_entry_completed") == true
        busy = waitingForChild
        setComposeContent {
            val status by network.state.collectAsState()
            BackHandler { finish() }
            Scaffold(containerColor = AppTheme.colors.overviewPageBackgroundColor,
                topBar = { SecondaryScaffoldHeader("绑定学号", ::finish) }) { padding ->
                if (resumeRequest || directEntry) {
                    Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AppTheme.colors.functionalTextColor)
                    }
                } else Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(shape = RoundedCornerShape(24.dp), color = AppTheme.colors.bgCardColor, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(20.dp)) {
                            Icon(if (bound) Icons.Outlined.CheckCircle else Icons.Outlined.Person, null,
                                Modifier.size(48.dp), tint = AppTheme.colors.functionalTextColor)
                            Text(if (bound) "学校账号已绑定" else "绑定学号", style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                                color = if (bound) AppTheme.colors.functionalTextColor else AppTheme.colors.primaryTextColor)
                            if (bound) {
                                Text("${StudentInfoManager.greeting} · ${StudentInfoManager.studentId}", textAlign = TextAlign.Center)
                                Button(onClick = { Route.goTimetable(this@BindingUserActivity) }) { Text("查看课表") }
                            } else if (busy) {
                                CircularProgressIndicator(color = AppTheme.colors.functionalTextColor)
                            } else Button(onClick = ::launchAuthentication, enabled = !busy) { Text("川农 e 认证登录") }
                        }
                    }
                    Surface(shape = RoundedCornerShape(16.dp), color = AppTheme.colors.bgCardColor, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            val connected = status == CampusNetworkStatus.VPN_CONNECTED
                            if (status == CampusNetworkStatus.CHECKING) {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else Icon(if (connected || status == CampusNetworkStatus.CAMPUS_DIRECT) Icons.Outlined.Language else Icons.Outlined.Close, null,
                                tint = if (connected) AppTheme.colors.functionalTextColor else AppTheme.colors.primaryTextColor)
                            Spacer(Modifier.width(12.dp))
                            Text(when (status) {
                                CampusNetworkStatus.CHECKING -> "正在检测校园网络…"
                                CampusNetworkStatus.VPN_CONNECTED -> "校园网络已连接"
                                CampusNetworkStatus.CAMPUS_DIRECT -> "当前校园网可用"
                                CampusNetworkStatus.NOT_CONNECTED -> "校园网络未连接"
                            }, modifier = Modifier.weight(1f))
                            if (bound && status == CampusNetworkStatus.NOT_CONNECTED) {
                                TextButton(enabled = !busy, onClick = ::launchAuthentication) { Text("连接") }
                            } else if (status != CampusNetworkStatus.CHECKING) {
                                IconButton(enabled = !busy, onClick = network::refresh) {
                                    Icon(Icons.Outlined.Refresh, "重新检测校园网络")
                                }
                            }
                        }
                    }
                    error?.let { Text(it, color = AppTheme.colors.primaryTextColor) }
                    if (bound) TextButton(onClick = { confirmUnbind = true }) { Text("解绑学校账号与清除会话", color = MaterialTheme.colorScheme.error) }
                }
            }
            if (confirmUnbind) AlertDialog(onDismissRequest = { confirmUnbind = false }, title = { Text("清除学校会话？") },
                text = { Text("清除本应用学校会话及当前账号学习缓存；平台账号保持登录。") },
                confirmButton = { TextButton(onClick = {
                    confirmUnbind = false; busy = true
                    lifecycleScope.launch {
                        try { AccountDataManager.clearSchoolBinding(); bound = false; error = null; network.refresh() }
                        catch (e: CancellationException) { throw e }
                        catch (_: Exception) { error = "解除绑定失败，请重试。" }
                        finally { busy = false }
                    }
                }) { Text("清除") } }, dismissButton = { TextButton(onClick = { confirmUnbind = false }) { Text("取消") } })
        }
        if (directEntry && !resumeRequest && !waitingForChild && !completed) {
            if (bound) {
                completed = true
                setResult(RESULT_OK)
                SchoolAccessGate.resumeLearningPage(this)
                finish()
            } else launchAuthentication()
        }
        if (resumeRequest && !waitingForChild) {
            busy = true
            lifecycleScope.launch {
                try {
                    com.mychuannong.app.school.schoolDiagnostic("binding=recovery_start")
                    val available = withTimeoutOrNull(45_000) {
                        SicauWebVpn.ensureAvailable() && SicauWebVpn.route() != null
                    } == true
                    com.mychuannong.app.school.schoolDiagnostic(if (available) "binding=vpn_ready" else "binding=vpn_unavailable")
                    if (available) {
                        waitingForChild = true
                        silentAcademic.launch(Intent(this@BindingUserActivity, UnifiedAcademicEntryActivity::class.java)
                            .putExtra(UnifiedAcademicEntryActivity.EXTRA_SILENT, true))
                    } else if (SicauWebVpn.phase.value == com.mychuannong.app.school.WebVpnPhase.AUTH_REQUIRED) {
                        launchAuthentication()
                    } else {
                        setResult(RESULT_FIRST_USER, Intent().putExtra("school_error", "校园网络暂时不可用，请检查网络后重试。"))
                        finish()
                    }
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) {
                    setResult(RESULT_FIRST_USER, Intent().putExtra("school_error", "学校会话暂时无法恢复，请检查网络后重试。"))
                    finish()
                }
            }
        }
    }

    private fun launchAuthentication() {
        if (waitingForChild) return
        waitingForChild = true
        busy = true
        authentication.launch(Intent(this, UnifiedSchoolAuthenticationActivity::class.java))
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("waiting_for_authentication", waitingForChild)
        outState.putBoolean("direct_school_entry", directEntry)
        outState.putBoolean("school_entry_completed", completed)
        super.onSaveInstanceState(outState)
    }

    override fun onResume() { super.onResume(); if (!isFinishing && !resumeRequest && !directEntry && !waitingForChild) network.refresh() }
    override fun onPause() { network.cancelCheck(); super.onPause() }
}
