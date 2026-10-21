package com.mychuannong.app.core.notifications

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mychuannong.app.core.theme.AppTheme

@Composable
fun AnnouncementPopupDialog(
    announcement: AnnouncementPopup,
    onDismiss: () -> Unit,
    onOpenDetail: () -> Unit,
) {
    AlertDialog(
        containerColor = AppTheme.colors.bgCardColor,
        onDismissRequest = onDismiss,
        title = { Text("系统公告", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = announcement.title.ifBlank { "重要公告" },
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppTheme.colors.primaryTextColor,
                )
                Text(
                    text = announcement.content.ifBlank { "暂无公告正文" },
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                    color = AppTheme.colors.secondaryTextColor,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("稍后查看") }
        },
        confirmButton = {
            TextButton(onClick = onOpenDetail) { Text("查看详情") }
        },
    )
}
