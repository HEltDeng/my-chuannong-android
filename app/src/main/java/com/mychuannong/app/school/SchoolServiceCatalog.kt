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
            context.startActivity(Intent(context,SchoolWebActivity::class.java).putExtra("url",url).putExtra("title",title))
        })
        return listOf(
            item("school-home","学校官网","https://www.sicau.edu.cn/",Icons.Outlined.Language,Color(0xFF237B62)),
            item("academic","教务系统",SicauAdapter.ACADEMIC,Icons.Outlined.School,Color(0xFF477DB3)),
            item("auth","统一认证",SicauAdapter.AUTH,Icons.Outlined.Lock,Color(0xFF6D6DB0)),
            item("ywtb","一网通办","https://ywtb.sicau.edu.cn/",Icons.Outlined.Apps,Color(0xFFAB8145)),
            item("payments","校园缴费","https://op.sicau.edu.cn/",Icons.Outlined.AccountBalanceWallet,Color(0xFF439D8C)),
            item("calendar","学校校历","https://www.sicau.edu.cn/ggfw/rlxl.htm",Icons.Outlined.CalendarMonth,Color(0xFFB86F7A)),
        )
    }
}
