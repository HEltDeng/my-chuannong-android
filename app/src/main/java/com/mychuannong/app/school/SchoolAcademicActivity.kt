package com.mychuannong.app.school

import android.os.Bundle
import com.mychuannong.app.SchoolWebActivity

/** Compatibility entry; the same service flow now owns academic authentication and Back. */
class SchoolAcademicActivity : SchoolWebActivity() {
    override fun onCreate(state: Bundle?) {
        intent.putExtra("url", SchoolServices.entries.getValue("academic").url)
            .putExtra("title", "教务系统")
            .putExtra(SchoolServiceNavigation.EXTRA_AUTH, SchoolServiceAuth.ACADEMIC.name)
        super.onCreate(state)
    }
    companion object {
        const val PUBLIC_URL = SicauJiaowuProtocol.LOGIN_PAGE
    }
}
