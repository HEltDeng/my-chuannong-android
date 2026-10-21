package com.mychuannong.app.feature.mooc.ui

import android.os.Bundle
import com.mychuannong.app.R
import com.mychuannong.app.base.ComposeActivity
import com.mychuannong.app.common.data.local.mmkv.StudentInfoManager.studentId
import com.mychuannong.app.common.data.local.mmkv.StudentInfoManager.studentPassword
import com.mychuannong.app.core.PlanetApplication
import com.mychuannong.app.core.Route
import com.mychuannong.app.widget.view.CustomToast

class MoocActivity : ComposeActivity() {
    private val moocViewModel by lazy { (application as PlanetApplication).moocViewModel }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Native preview stays accessible before school identity is verified.

        setComposeContent {
            MoocScreen(
                moocViewModel = moocViewModel,
                onBack = ::finish,
                onOpenCoursePage = { courseId ->
                    Route.goMoocCoursePage(this, courseId)
                }
            )
        }
    }

    override fun onResume() {
        super.onResume()
        if (studentId.isNotEmpty() && studentPassword.isNotEmpty() && moocViewModel.shouldAutoRefreshOnEnter()) {
            moocViewModel.refreshCourses()
        }
    }
}
