package com.mychuannong.app.feature.mooc.ui
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mychuannong.app.core.theme.AppSkinTheme
class MoocCoursePageActivity:ComponentActivity() {
 companion object { const val EXTRA_COURSE_ID="extra_course_id" }
 override fun onCreate(state:Bundle?) { super.onCreate(state);setContent { AppSkinTheme { Surface {
  Column(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
   TextButton(onClick={finish()}){Text("返回")}
   Text("课程作业",style=MaterialTheme.typography.headlineMedium)
   Text("教学平台来源待确认")
   Text("原生课程与作业列表已保留。四川农业大学的作业平台、课程详情入口和会话规则尚未核验，当前不会套用其他学校的业务地址，也不会清除学校网页登录状态。")
  }
 } } } }
}
