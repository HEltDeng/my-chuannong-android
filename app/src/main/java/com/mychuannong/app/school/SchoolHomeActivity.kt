package com.mychuannong.app.school

import com.mychuannong.app.core.web.ServiceWebActivity

/** Keep the existing route; the university renders and operates its original website. */
class SchoolHomeActivity : ServiceWebActivity() {
    override val serviceTitle = "学校官网"
    override val initialUrl = "https://www.sicau.edu.cn/"
}
