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
import androidx.lifecycle.viewModelScope
import com.mychuannong.app.base.ComposeActivity
import com.mychuannong.app.core.Route
import com.mychuannong.app.core.theme.AppTheme
import com.mychuannong.app.core.theme.SecondaryScaffoldHeader
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

internal class CampusNetworkViewModel : ViewModel() {
    private val mutable = MutableStateFlow(CampusNetworkStatus.CHECKING)
    val state = mutable.asStateFlow()
    private var job: Job? = null
    private val detector = CampusNetworkDetector(
        vpnConnected = {
            withTimeoutOrNull(12_000) {
                SicauWebVpn.check(force = true)
            } == true
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
            combine(SicauWebVpn.networkRevision, SicauNativeAuth.webRevision) { network, session -> network to session }
                .collectLatest { (network, session) ->
                    mutable.value = CampusNetworkStatus.CHECKING
                    val result = detector.check()
                    if (network == SicauWebVpn.networkRevision.value && session == SicauNativeAuth.webRevision.value)
                        mutable.value = result
                }
        }
    }

    fun cancelCheck() { job?.cancel() }
}

/** The settings entry stays on a native status page after VPN authentication returns. */
class CampusNetworkActivity : ComposeActivity() {
    private val task: CampusNetworkViewModel by viewModels()
    private val vpnLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        // onResume checks the actual school network after returning.
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
                Column(Modifier.fillMaxWidth().padding(24.dp).semantics { liveRegion = LiveRegionMode.Polite },
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Icon(if (connected) Icons.Outlined.CheckCircle else Icons.Outlined.Language,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp).align(Alignment.CenterHorizontally),
                        tint = MaterialTheme.colorScheme.primary)
                    Text(when (status) {
                        CampusNetworkStatus.CHECKING -> "正在检查校园网络环境…"
                        CampusNetworkStatus.VPN_CONNECTED -> "你现在已经连接校园 VPN"
                        CampusNetworkStatus.CAMPUS_DIRECT -> "当前校园网可用"
                        CampusNetworkStatus.NOT_CONNECTED -> "校园 VPN 尚未连接"
                    }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    if (status != CampusNetworkStatus.CAMPUS_DIRECT) {
                        Text(when (status) {
                            CampusNetworkStatus.CHECKING -> "正在验证校园 VPN 和教务系统的连接状态。"
                            CampusNetworkStatus.VPN_CONNECTED -> "已确认校园 VPN 通道可用，教务登录将优先使用此通道。"
                            CampusNetworkStatus.CAMPUS_DIRECT -> ""
                            CampusNetworkStatus.NOT_CONNECTED -> "暂未确认可用的校园通道。请绑定校园 VPN；如已绑定，请检查网络后重新检测。"
                        }, style = MaterialTheme.typography.bodyMedium)
                    }
                    if (checking) LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (!connected) Button(onClick = onBindVpn, modifier = Modifier.fillMaxWidth()) {
                        Text("川农 e 认证登录")
                    }
                    if (connected) Button(
                        onClick = onAcademicLogin, modifier = Modifier.fillMaxWidth()
                    ) { Text("继续登录教务系统") }
                    TextButton(onClick = onRefresh, enabled = !checking, modifier = Modifier.align(Alignment.End)) {
                        Text("重新检测")
                    }
                }
            }
        }
    }
}
