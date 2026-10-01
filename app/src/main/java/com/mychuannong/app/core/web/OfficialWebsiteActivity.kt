package com.mychuannong.app.core.web
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mychuannong.app.core.theme.AppSkinTheme
internal fun isOfficialWebsiteUrl(rawUrl:String):Boolean = false
class OfficialWebsiteActivity:ComponentActivity() {
 companion object {
  fun createIntent(context:Context,url:String="")=Intent(context,OfficialWebsiteActivity::class.java)
 }
 override fun onCreate(state:Bundle?) { super.onCreate(state);setContent { AppSkinTheme {
  Surface { Column(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
   TextButton(onClick={finish()}) { Text("返回") }
   Text("我的川农官网",style=MaterialTheme.typography.headlineMedium)
   Text("官网筹备中")
   Text("我的川农是独立校园便利工具，非四川农业大学官方 App。当前为内部预览，尚未配置产品官网与公开下载地址。隐私说明、用户协议和反馈仍可在应用内查看。")
  } }
 } } }
}
