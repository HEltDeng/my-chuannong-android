package com.mychuannong.app.school

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** Startup check is once per activity instance; dismissing authentication leaves caches accessible. */
@Composable
fun WebVpnEntry() {
    val context = LocalContext.current
    val phase by SicauWebVpn.phase.collectAsState()
    var started by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if(result.resultCode == android.app.Activity.RESULT_OK && com.mychuannong.app.common.data.local.mmkv.StudentInfoManager.hasCompleteBinding)
            scope.launch { SchoolSyncEngine.sync(force = true) }
    }
    fun connect() = launcher.launch(Intent(context, SicauWebVpnActivity::class.java))
    LaunchedEffect(Unit) {
        if(!started) { started = true; SicauWebVpn.check() }
    }
    Row(Modifier.statusBarsPadding().padding(horizontal = 16.dp)) {
        TextButton(onClick = { connect() }) {
            Text(when(phase) {
                WebVpnPhase.AVAILABLE -> "校内服务登录"
                WebVpnPhase.AUTH_REQUIRED -> "校内服务登录"
                WebVpnPhase.NETWORK_ERROR -> "校内服务登录"
                WebVpnPhase.AUTHENTICATING, WebVpnPhase.RESOURCE_FOUND -> "正在登录"
                else -> "校内服务登录"
            })
        }
    }
}
