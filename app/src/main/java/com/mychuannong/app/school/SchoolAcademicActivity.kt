package com.mychuannong.app.school
import android.content.Intent
import android.os.Bundle
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mychuannong.app.base.ComposeActivity
import com.mychuannong.app.SchoolWebActivity
import com.mychuannong.app.core.theme.AppTheme
import com.mychuannong.app.feature.timetable.ui.TimeTableActivity
import com.mychuannong.app.feature.common.ui.ScoreInquiryActivity
import com.mychuannong.app.feature.common.ui.ExamArrangementActivity

class SchoolAcademicActivity:ComposeActivity() {
 override fun onCreate(state:Bundle?) {super.onCreate(state)
  fun official(){startActivity(Intent(this,SchoolWebActivity::class.java).putExtra("url",SicauAdapter.ACADEMIC).putExtra("title","教务系统"))}
  setComposeContent {
   Scaffold(containerColor=AppTheme.colors.bgPrimaryColor,topBar={SchoolPageTopBar("教务系统",{finish()},{recreate()},listOf("在浏览器中打开" to {openOfficialBrowser(this,SicauAdapter.ACADEMIC)},"关闭本页" to {finish()},"打开官方业务页面" to {official()}))}) { padding ->
    Column(Modifier.fillMaxSize().padding(padding).padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
     Text("学习查询",style=MaterialTheme.typography.titleLarge,color=AppTheme.colors.titleTopColor)
     listOf("课表" to TimeTableActivity::class.java,"成绩查询" to ScoreInquiryActivity::class.java,"考试安排" to ExamArrangementActivity::class.java).forEach{(title,activity)->OutlinedButton(onClick={startActivity(Intent(this@SchoolAcademicActivity,activity))},modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)){Text(title)}}
     Text("其他教务业务",style=MaterialTheme.typography.titleMedium)
     OutlinedButton(onClick={startActivity(Intent(this@SchoolAcademicActivity,TimetableImportActivity::class.java))},modifier=Modifier.fillMaxWidth()){Text("从图片导入课表")}
     Button(onClick={official()},modifier=Modifier.fillMaxWidth()){Text("进入官方教务系统")}
    }
   }
  }
 }
}
