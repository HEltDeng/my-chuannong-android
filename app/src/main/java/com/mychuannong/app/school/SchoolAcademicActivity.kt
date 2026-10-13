package com.mychuannong.app.school

import com.mychuannong.app.core.web.ServiceWebActivity

/** Display the original academic website using the same in-app container as school home. */
class SchoolAcademicActivity : ServiceWebActivity() {
    override val serviceTitle = "教务系统"
    override val initialUrl = PUBLIC_URL

    companion object {
        const val PUBLIC_URL = "https://jiaowu.sicau.edu.cn/web/web/web/index.asp"
    }
}
