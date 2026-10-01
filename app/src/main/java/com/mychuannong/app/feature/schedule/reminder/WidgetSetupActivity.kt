package com.mychuannong.app.feature.schedule.reminder

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mychuannong.app.NextCourseAppWidget
import com.mychuannong.app.TimeTableAppWidget
import com.mychuannong.app.WidgetUpdateCoordinator
import com.mychuannong.app.base.ComposeActivity
import com.mychuannong.app.core.theme.AppTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class WidgetSetupActivity : ComposeActivity() {
    private var message by mutableStateOf("")
    private var pinSucceeded by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleResult(intent)
        setComposeContent {
            WidgetSetupScreen(
                message = message,
                showHomeAction = pinSucceeded,
                onBack = ::finish,
                onAddNext = { requestWidget(NextCourseAppWidget::class.java, 21001) },
                onAddToday = { requestWidget(TimeTableAppWidget::class.java, 21002) },
                onHome = ::openHome,
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleResult(intent)
    }

    private fun handleResult(intent: Intent?) {
        if (intent?.action == ACTION_WIDGET_PINNED) {
            pinSucceeded = true
            message = "已添加到桌面"
            CoroutineScope(Dispatchers.IO).launch { WidgetUpdateCoordinator.updateAll(this@WidgetSetupActivity) }
        }
    }

    private fun requestWidget(provider: Class<*>, requestCode: Int) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            unsupported()
            return
        }
        val manager = AppWidgetManager.getInstance(this)
        if (!manager.isRequestPinAppWidgetSupported) {
            unsupported()
            return
        }
        val callback = PendingIntent.getActivity(
            this,
            requestCode,
            Intent(this, WidgetSetupActivity::class.java).setAction(ACTION_WIDGET_PINNED),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val requested = runCatching {
            manager.requestPinAppWidget(ComponentName(this, provider), null, callback)
        }.getOrDefault(false)
        message = if (requested) "请在系统桌面确认添加" else unsupportedText()
        pinSucceeded = false
    }

    private fun unsupported() {
        pinSucceeded = false
        message = unsupportedText()
    }

    private fun unsupportedText() = "当前桌面不支持从应用直接添加。请返回桌面，长按空白处 -> 小组件 -> 我的川农。"

    private fun openHome() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    companion object { private const val ACTION_WIDGET_PINNED = "com.mychuannong.app.action.WIDGET_PINNED" }
}

@Composable
private fun WidgetSetupScreen(
    message: String,
    showHomeAction: Boolean,
    onBack: () -> Unit,
    onAddNext: () -> Unit,
    onAddToday: () -> Unit,
    onHome: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ReminderHeader("桌面小组件", onBack)
        Text("在桌面快速查看下一节课和今日剩余课程。", color = AppTheme.colors.secondaryTextColor)
        WidgetChoice("下一节课", "2×2 · 课程、地点、开始时间与倒计时", onAddNext)
        WidgetChoice("今日课表", "4×2 · 今日后续课程并突出下一节", onAddToday)
        ReminderCard {
            Text("也可在桌面长按空白处，选择‘小组件’，找到‘我的川农’后添加。", color = AppTheme.colors.secondaryTextColor)
        }
        if (message.isNotBlank()) {
            ReminderCard {
                Text(message, color = AppTheme.colors.primaryTextColor)
                if (showHomeAction) OutlinedButton(onClick = onHome) { Text("返回桌面查看") }
            }
        }
    }
}

@Composable
private fun WidgetChoice(title: String, subtitle: String, onClick: () -> Unit) {
    ReminderCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, color = AppTheme.colors.primaryTextColor)
                Text(subtitle, color = AppTheme.colors.secondaryTextColor)
            }
            Button(onClick = onClick) { Text("添加") }
        }
    }
}
