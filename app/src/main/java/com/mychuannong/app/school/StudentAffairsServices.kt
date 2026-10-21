package com.mychuannong.app.school

import android.content.Context

object StudentAffairsServices {
    val entries = SchoolServices.entries.filterKeys {
        it in setOf("comprehensive_score", "weekly_attendance", "holiday_destination")
    }
    fun open(context: Context, id: String) = SchoolServiceNavigation.open(context, entries.getValue(id))
}
