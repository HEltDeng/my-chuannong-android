package com.mychuannong.app.feature.common.ui
import android.os.Bundle
import android.content.ClipData
import android.content.ClipboardManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mychuannong.app.core.theme.AppSkinTheme
class ElectronicActivity:ComponentActivity() {
 override fun onCreate(state:Bundle?) {super.onCreate(state);setContent {AppSkinTheme { Surface {Column(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
  TextButton(onClick={finish()}) {Text("返回")}
  Text("电费查询",style=MaterialTheme.typography.headlineMedium)
  Card {Column(Modifier.padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
   Text("川农后勤 · 电精灵",style=MaterialTheme.typography.titleLarge)
   Text("1. 在微信中搜索并核对官方公众号“川农后勤”。\n2. 从公众号菜单进入“电精灵”。\n3. 按官方页面要求登录，自行选择寝室并查询。")
   Button(onClick={(getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("公众号","川农后勤"))}) {Text("复制公众号名称")}
   Text("完整业务地址尚未核验。本应用不显示未经查询的余额，也不代为绑定寝室。")
  }}
 }}}}
}

}
