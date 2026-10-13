package com.mychuannong.app.school

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import com.mychuannong.app.base.ComposeActivity
import com.mychuannong.app.core.Route
import com.mychuannong.app.core.theme.AppTheme
import com.mychuannong.app.core.theme.SecondaryScaffoldHeader
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

internal class CampusNetworkViewModel : ViewModel() {
    private val mutable = MutableStateFlow(CampusNetworkStatus.CHECKING)
    val state = mutable.asStateFlow()
    private var job: Job? = null
    private val detector = CampusNetworkDetector(
        vpnConnected = {
            SicauNativeAuth.exportToWebView()
            withTimeoutOrNull(12_000) { SicauWebVpn.check(force = true) } == true
        },
        directReachable = {
            val session = SicauJiaowuSession(SicauJiaowuCookieJar(SicauJiaowuRoute.Direct))
            try { withTimeoutOrNull(8_000) { session.checkLoginPage(); true } == true }
            finally { session.cancel() }
        },
    )

    fun refresh() {
        job?.cancel()
        mutable.value = CampusNetworkStatus.CHECKING
        job = viewModelScope.launch {
            val network = SicauWebVpn.networkRevision.value
            val session = SicauNativeAuth.webRevision.value
            val result = detector.check()
            if (network == SicauWebVpn.networkRevision.value && session == SicauNativeAuth.webRevision.value)
                mutable.value = result
        }
    }

    fun cancelCheck() { job?.cancel() }
}

/** The settings entry stays on a native status page after VPN authentication returns. */
class CampusNetworkActivity : ComposeActivity() {
    private val task: CampusNetworkViewModel by viewModels()
    private val vpnLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        task.refresh() // Even RESULT_OK must be checked against the current network/session.
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setComposeContent {
            val status by task.state.collectAsState()
            BackHandler { finish() }
            CampusNetworkScreen(status, ::finish, {
                vpnLauncher.launch(Intent(this, SicauWebVpnActivity::class.java))
            }, task::refresh, { Route.goBindingUser(this, reauthenticate = true) })
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                launch { SicauWebVpn.networkRevision.drop(1).collect { task.refresh() } }
                launch { SicauNativeAuth.webRevision.drop(1).collect { task.refresh() } }
            }
        }
    }

    override fun onResume() { super.onResume(); task.refresh() }
    override fun onPause() { task.cancelCheck(); super.onPause() }
}

@Composable
internal fun CampusNetworkScreen(
    status: CampusNetworkStatus,
    onBack: () -> Unit,
    onBindVpn: () -> Unit,
    onRefresh: () -> Unit,
    onAcademicLogin: () -> Unit,
) {
    val checking = status == CampusNetworkStatus.CHECKING
    val connected = status == CampusNetworkStatus.VPN_CONNECTED
    Scaffold(containerColor = AppTheme.colors.overviewPageBackgroundColor, topBar = {
        SecondaryScaffoldHeader(CampusLoginCopy.networkTitle, onBack)
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = AppTheme.colors.bgCardColor),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
                Column(Modifier.padding(24.dp).semantics { liveRegion = LiveRegionMode.Polite },
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Icon(if (connected) Icons.Outlined.CheckCircle else Icons.Outlined.Language,
                        contentDescription = null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(when (status) {
                        CampusNetworkStatus.CHECKING -> "正在检查校园网络环境…"
                        CampusNetworkStatus.VPN_CONNECTED -> "你现在已经连接校园 VPN"
                        CampusNetworkStatus.CAMPUS_DIRECT -> "当前校园网直连通道可用"
                        CampusNetworkStatus.NOT_CONNECTED -> "校园 VPN 尚未连接"
                    }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(when (status) {
                        CampusNetworkStatus.CHECKING -> "正在验证校园 VPN 和教务系统的连接状态。"
                        CampusNetworkStatus.VPN_CONNECTED -> "已确认校园 VPN 通道可用，教务登录将优先使用此通道。"
                        CampusNetworkStatus.CAMPUS_DIRECT -> "已确认教务系统可直接访问。推荐继续绑定校园 VPN，方便切换到校外网络后使用。"
                        CampusNetworkStatus.NOT_CONNECTED -> "暂未确认可用的校园通道。请绑定校园 VPN；如已绑定，请检查网络后重新检测。"
                    }, style = MaterialTheme.typography.bodyMedium)
                    if (checking) LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (!connected) Button(onClick = onBindVpn, modifier = Modifier.fillMaxWidth()) {
                        Text("绑定校园 VPN（推荐）")
                    }
                    if (connected) Button(
                        onClick = onAcademicLogin, modifier = Modifier.fillMaxWidth()
                    ) { Text("继续登录教务系统") }
                    else if (status == CampusNetworkStatus.CAMPUS_DIRECT) OutlinedButton(
                        onClick = onAcademicLogin, modifier = Modifier.fillMaxWidth()
                    ) { Text("使用校园网继续登录") }
                    TextButton(onClick = onRefresh, enabled = !checking, modifier = Modifier.align(Alignment.End)) {
                        Text("重新检测")
                    }
                }
            }
            Text("推荐绑定校园 VPN，无论校内还是校外，均优先使用 VPN 通道。VPN 连接成功后，仍需完成教务账号登录。",
                style = MaterialTheme.typography.bodyMedium)
        }
    }
}
