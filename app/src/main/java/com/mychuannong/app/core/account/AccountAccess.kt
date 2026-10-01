package com.mychuannong.app.core.account

import android.content.Context
import android.content.Intent
import com.mychuannong.app.core.account.ui.AccountRequiredActivity
import com.mychuannong.app.core.network.campustools.CampusToolsSession

object AccountAccess {
    val isSignedIn: Boolean
        get() = CampusToolsSession.currentUser != null && !CampusToolsSession.token.isNullOrBlank()

    fun openOrRequestLogin(context: Context, target: Intent) {
        if (isSignedIn) context.startActivity(target)
        else context.startActivity(AccountRequiredActivity.intent(context, target))
    }

    fun requireLogin(context: Context, returnTarget: Intent): Boolean {
        if (isSignedIn) return true
        context.startActivity(AccountRequiredActivity.intent(context, returnTarget))
        return false
    }
}
