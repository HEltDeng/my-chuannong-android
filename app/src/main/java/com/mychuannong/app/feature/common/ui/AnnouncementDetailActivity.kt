package com.mychuannong.app.feature.common.ui

import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import com.mychuannong.app.core.theme.SecondaryPageHeader
import com.mychuannong.app.base.ComposeActivity
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
class AnnouncementDetailActivity : ComposeActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val content = intent.getStringExtra(EXTRA_CONTENT).orEmpty()
        val publishedAt = intent.getLongExtra(EXTRA_PUBLISHED_AT, 0L)
        setComposeContent {
            AppSkinTheme {
                AnnouncementDetailPage(
                    title = title,
                    content = content,
                    publishedAt = publishedAt,
                    imageUrl = intent.getStringExtra("image_url"),
                    category = intent.getStringExtra("category"),
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
            imageUrl: String? = null,
            category: String? = null,
        ): Intent = Intent(context, AnnouncementDetailActivity::class.java)
            .putExtra("image_url", imageUrl)
            .putExtra("category", category)
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
    imageUrl: String?,
    category: String?,
    onBack: () -> Unit,
) {
    val colors = AppTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.overviewPageBackgroundColor)
            .padding(horizontal = 16.dp),
    ) {
        SecondaryPageHeader("服务公告", onBack)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(colors.bgCardColor)
                .border(1.dp, colors.outlineLowContrastColor, RoundedCornerShape(22.dp))
                .padding(20.dp),
        ) {
            Text(
                text = title.ifBlank { "校园公告" },
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = colors.primaryTextColor,
            )
            category?.takeIf { it.isNotBlank() }?.let { Text(it, color = colors.commonColor) }
            imageUrl?.let { coil.compose.AsyncImage(model = com.mychuannong.app.core.network.campustools.CampusToolsApiClient.absoluteUrl(it), contentDescription = "公告图片", modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) }
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
