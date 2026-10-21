package com.mychuannong.app.school

import android.content.Context
import android.content.Intent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.Color
import com.mychuannong.app.SchoolWebActivity
import com.mychuannong.app.feature.common.compose_ui.FunctionItemData

/** Document-provided or verified public entries. No guessed authenticated routes. */
object SchoolServiceCatalog {
    const val productId="my-chuannong"
    const val schoolId="sicau"
    val campuses=listOf("雅安校区","成都校区","都江堰校区")
    fun items(context:Context):List<FunctionItemData> {
        fun item(id:String,title:String,url:String,icon:androidx.compose.ui.graphics.vector.ImageVector,color:Color)=FunctionItemData(id,title,icon,color,onClick={
            if (id == "school-home") context.startActivity(Intent(context,SchoolHomeActivity::class.java))
            else if (id in SchoolServices.entries) SchoolServiceNavigation.open(context, id)
            else if (id == "calendar") context.startActivity(Intent(context,com.mychuannong.app.feature.common.ui.CalendarActivity::class.java))
            else context.startActivity(Intent(context,SchoolWebActivity::class.java).putExtra("url",url).putExtra("title",title))
        })
        return listOf(
            item("school-home","学校官网","https://www.sicau.edu.cn/",Icons.Outlined.Language,Color(0xFF237B62)),
            item("academic","教务系统",SchoolAcademicActivity.PUBLIC_URL,Icons.Outlined.School,Color(0xFF477DB3)),
            item("student-affairs","学工系统","https://xsgz.sicau.edu.cn/mobile/stuhall/studentindex",Icons.Outlined.AssignmentInd,Color(0xFF477DB3)),
            item("auth","统一认证",SicauAdapter.AUTH,Icons.Outlined.Lock,Color(0xFF6D6DB0)),
            item("ywtb","一网通办","https://ywtb.sicau.edu.cn/",Icons.Outlined.Apps,Color(0xFFAB8145)),
            item("payments","校园缴费","https://op.sicau.edu.cn/CasLogin.aspx",Icons.Outlined.AccountBalanceWallet,Color(0xFF439D8C)),
            item("calendar","学校校历","https://www.sicau.edu.cn/ggfw/rlxl.htm",Icons.Outlined.CalendarMonth,Color(0xFFB86F7A)),
            FunctionItemData("campus-map", "校园地图", Icons.Outlined.Map, Color(0xFF237B62), onClick = {
                context.startActivity(Intent(context, CampusMapActivity::class.java))
            }),
        ) + SchoolServices.entries.filterKeys {
            it !in setOf("comprehensive_score", "weekly_attendance", "holiday_destination",
                "academic", "student-affairs", "auth", "ywtb", "payments")
        }.map { (id, service) -> item(id, service.title, service.url, Icons.Outlined.Apps, Color(0xFF237B62)) }
    }
}
