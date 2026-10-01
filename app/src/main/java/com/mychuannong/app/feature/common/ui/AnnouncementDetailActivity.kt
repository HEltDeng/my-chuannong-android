package com.mychuannong.app.feature.common.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mychuannong.app.core.theme.AppSkinTheme
import com.mychuannong.app.core.theme.AppTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Minimal announcement detail page opened from the service-dynamics
 * announcement card. Renders title, body and publish time only.
 */
class AnnouncementDetailActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val content = intent.getStringExtra(EXTRA_CONTENT).orEmpty()
        val publishedAt = intent.getLongExtra(EXTRA_PUBLISHED_AT, 0L)
        setContent {
            AppSkinTheme {
                AnnouncementDetailPage(
                    title = title,
                    content = content,
                    publishedAt = publishedAt,
                    onBack = ::finish,
                )
            }
        }
    }

    companion object {
        private const val EXTRA_TITLE = "extra_title"
        private const val EXTRA_CONTENT = "extra_content"
        private const val EXTRA_PUBLISHED_AT = "extra_published_at"

        fun intent(
            context: Context,
            title: String,
            content: String,
            publishedAt: Long?,
        ): Intent = Intent(context, AnnouncementDetailActivity::class.java)
            .putExtra(EXTRA_TITLE, title)
            .putExtra(EXTRA_CONTENT, content)
            .apply {
                if (publishedAt != null) putExtra(EXTRA_PUBLISHED_AT, publishedAt)
            }
    }
}

@Composable
private fun AnnouncementDetailPage(
    title: String,
    content: String,
    publishedAt: Long,
    onBack: () -> Unit,
) {
    val colors = AppTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.padding(start = 8.dp, top = 6.dp),
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            Text(
                text = title.ifBlank { "校园公告" },
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = colors.primaryTextColor,
            )
            if (publishedAt > 0L) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(publishedAt)),
                    fontSize = 12.sp,
                    color = colors.greyTextColor,
                )
            }
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = content.ifBlank { "暂无公告正文" },
                fontSize = 15.sp,
                lineHeight = 23.sp,
                color = colors.primaryTextColor,
            )
            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}
