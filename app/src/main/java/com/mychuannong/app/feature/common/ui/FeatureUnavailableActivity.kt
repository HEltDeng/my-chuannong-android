package com.mychuannong.app.feature.common.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mychuannong.app.core.theme.AppSkinTheme
import com.mychuannong.app.core.theme.AppTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A minimal, login-free page shown when a user taps a MAINTENANCE / CLOSED
 * service update. It renders the module name, status and the exact
 * user-facing message configured by the backend. It never grants access to
 * the underlying module.
 */
class FeatureUnavailableActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "功能暂不可用"
        val status = intent.getStringExtra(EXTRA_STATUS) ?: "CLOSED"
        val message = intent.getStringExtra(EXTRA_MESSAGE).orEmpty()
        val recoverAt = if (intent.hasExtra(EXTRA_RECOVER_AT)) {
            intent.getLongExtra(EXTRA_RECOVER_AT, 0L).takeIf { it > 0L }
        } else {
            null
        }
        setContent {
            AppSkinTheme {
                FeatureUnavailablePage(
                    title = title,
                    status = status,
                    message = message,
                    recoverAt = recoverAt,
                    onBack = ::finish,
                )
            }
        }
    }

    companion object {
        private const val EXTRA_TITLE = "extra_title"
        private const val EXTRA_STATUS = "extra_status"
        private const val EXTRA_MESSAGE = "extra_message"
        private const val EXTRA_RECOVER_AT = "extra_recover_at"

        fun intent(
            context: Context,
            title: String,
            status: String,
            message: String,
            recoverAt: Long?,
        ): Intent = Intent(context, FeatureUnavailableActivity::class.java)
            .putExtra(EXTRA_TITLE, title)
            .putExtra(EXTRA_STATUS, status)
            .putExtra(EXTRA_MESSAGE, message)
            .apply {
                if (recoverAt != null) putExtra(EXTRA_RECOVER_AT, recoverAt)
            }
    }
}

@Composable
private fun FeatureUnavailablePage(
    title: String,
    status: String,
    message: String,
    recoverAt: Long?,
    onBack: () -> Unit,
) {
    val colors = AppTheme.colors
    val maintenance = status.equals("MAINTENANCE", ignoreCase = true)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 16.dp),
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.padding(top = 6.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.ArrowBack,
                contentDescription = "返回",
                tint = colors.primaryTextColor,
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 64.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = if (maintenance) Icons.Rounded.Build else Icons.Rounded.EventBusy,
                contentDescription = null,
                tint = if (maintenance) Color(0xFFB8860B) else colors.greyTextColor,
                modifier = Modifier.height(64.dp),
            )
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = title,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = colors.primaryTextColor,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (maintenance) "功能维护中" else "功能暂不开放",
                fontSize = 14.sp,
                color = colors.secondaryTextColor,
            )
            if (message.isNotBlank()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = message,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    color = colors.primaryTextColor,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
            recoverAt?.let { millis ->
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "预计恢复：${SimpleDateFormat("M月d日 HH:mm", Locale.CHINA).format(Date(millis))}",
                    fontSize = 13.sp,
                    color = colors.greyTextColor,
                )
            }
        }
    }
}
