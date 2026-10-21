package com.mychuannong.app.core.account

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.mychuannong.app.base.ComposeActivity
import com.mychuannong.app.core.account.ui.AccountRequiredActivity
import com.mychuannong.app.core.network.campustools.CampusToolsSession
import kotlinx.coroutines.launch

/** Protect the page itself, including restored tasks and direct notification/deep-link entries. */
abstract class AccountProtectedActivity : ComposeActivity() {
    final override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!checkAccount()) return
        onAccountReady(savedInstanceState)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                CampusToolsSession.state.collect { checkAccount() }
            }
        }
    }

    protected abstract fun onAccountReady(savedInstanceState: Bundle?)

    override fun onResume() {
        super.onResume()
        checkAccount()
    }

    private fun checkAccount(): Boolean {
        if (isFinishing) return false
        if (AccountAccess.isSignedIn) return true
        window.decorView.visibility = View.GONE
        // Preserve the selected module/post, but never replay task-clearing launch flags.
        val target = Intent(intent).apply { flags = 0; selector = null }
        startActivity(AccountRequiredActivity.intent(this, target))
        finish()
        return false
    }
}
