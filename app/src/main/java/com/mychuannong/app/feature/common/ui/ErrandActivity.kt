package com.mychuannong.app.feature.common.ui

import android.content.ContentValues
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.mychuannong.app.R
import com.mychuannong.app.core.account.AccountProtectedActivity
import com.mychuannong.app.core.theme.AppTheme
import com.mychuannong.app.core.theme.SecondaryScaffoldHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** User-approved scan fallback. No QR decoding, invented deep link, credentials or payment handling. */
class ErrandActivity : AccountProtectedActivity() {
    private var message by mutableStateOf("")
    private var saving by mutableStateOf(false)
    private val requestStorage = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) saveCode() else message = "未获得相册保存权限，请允许后重试。"
    }
    override fun onAccountReady(savedInstanceState: Bundle?) {
        setComposeContent {
            Scaffold(containerColor=AppTheme.colors.bgPrimaryColor,topBar={
                SecondaryScaffoldHeader("跑腿代取",::finish) { Text("第三方服务", style = MaterialTheme.typography.labelSmall, color = AppTheme.colors.secondaryTextColor) }
            }) { padding ->
                Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
                    verticalArrangement=Arrangement.spacedBy(16.dp)) {
            ManagedServiceGuide("errand")
                    Image(painterResource(R.drawable.errand_service_poster), "哈哈龙跑腿宣传海报与小程序码",
                        modifier = Modifier.fillMaxWidth().aspectRatio(1320f / 1770f).clip(RoundedCornerShape(24.dp)), contentScale = ContentScale.Crop)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = ::saveCode, enabled = !saving, modifier = Modifier.weight(1f)) {
                            Text(if (saving) "正在保存…" else "保存海报")
                        }
                        OutlinedButton(onClick = ::openWeChat, modifier = Modifier.weight(1f)) {
                            Text("打开微信")
                        }
                    }
                    if (message.isNotBlank()) Text(message, color = AppTheme.colors.functionalTextColor,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.semantics { liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite })
                    Text(
                        "本服务由“哈哈龙跑腿”第三方平台提供。川农APP仅提供信息入口，不参与运营、接单、配送、定价、支付或售后。",
                        color = AppTheme.colors.secondaryTextColor,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
    private fun openWeChat() {
        val intent = packageManager.getLaunchIntentForPackage("com.tencent.mm")
        if (intent == null) {
            message = "当前未检测到微信，请先安装微信，或保存海报后使用其他设备扫码。"
            return
        }
        runCatching { startActivity(intent) }
            .onSuccess { message = "" }
            .onFailure { message = "暂时无法打开微信，请重试。" }
    }

    // openRawResource also accepts file-backed drawable resources; retain the exact code image bytes.
    @android.annotation.SuppressLint("ResourceType")
    private fun copyCode(uri: android.net.Uri) {
        requireNotNull(contentResolver.openOutputStream(uri)).use { output ->
            resources.openRawResource(R.drawable.errand_service_poster).use { it.copyTo(output) }
        }
    }
    private fun saveCode() {
        if(saving) return
        if (Build.VERSION.SDK_INT < 29 && androidx.core.content.ContextCompat.checkSelfPermission(this,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestStorage.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
            return
        }
        lifecycleScope.launch {
            saving = true
            val ok = withContext(Dispatchers.IO) {
                var uri: android.net.Uri? = null
                runCatching {
                    uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME,"跑腿代取_${System.currentTimeMillis()}.jpg")
                        put(MediaStore.Images.Media.MIME_TYPE,"image/jpeg")
                        if (Build.VERSION.SDK_INT >= 29) {
                            put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/MyChuannong")
                            put(MediaStore.Images.Media.IS_PENDING,1)
                        } else {
                            val directory = java.io.File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES), "MyChuannong")
                            check(directory.exists() || directory.mkdirs())
                            put(MediaStore.Images.Media.DATA, java.io.File(directory, "跑腿代取_${System.currentTimeMillis()}.jpg").absolutePath)
                        }
                    })
                    copyCode(requireNotNull(uri))
                    if (Build.VERSION.SDK_INT >= 29) {
                        check(contentResolver.update(requireNotNull(uri),ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING,0) },null,null) > 0)
                    }
                }.onFailure { uri?.let { runCatching { contentResolver.delete(it,null,null) } } }.isSuccess
            }
            saving = false
            message = if(ok) "海报已保存到相册" else "保存失败，请重试。"
        }
    }
}
