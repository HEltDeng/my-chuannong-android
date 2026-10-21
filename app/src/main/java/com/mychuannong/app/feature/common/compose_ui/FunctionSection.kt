package com.mychuannong.app.feature.common.compose_ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mychuannong.app.core.theme.AppSkinTheme
import com.mychuannong.app.core.theme.AppTheme

@Composable
fun FunctionSection(
    title: String,
    description: String,
    items: List<FunctionItemData>,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = AppTheme.colors.bgCardColor,
        border = BorderStroke(
            width = 1.dp,
            color = AppTheme.colors.outlineLowContrastColor.copy(alpha = 0.34f),
        ),
        shadowElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Text(
                text = title,
                color = AppTheme.colors.primaryTextColor,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = description,
                color = AppTheme.colors.secondaryTextColor,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                modifier = Modifier.padding(top = 2.dp, bottom = 6.dp),
            )

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items.chunked(GRID_COLUMN_COUNT).forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start,
                    ) {
                        rowItems.forEach { item ->
                            FunctionItem(
                                item = item,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        val remainingItems = GRID_COLUMN_COUNT - rowItems.size
                        if (remainingItems > 0) {
                            Spacer(modifier = Modifier.weight(remainingItems.toFloat()))
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun FunctionSectionPreview() {
    AppSkinTheme {
        FunctionSection(
            title = "学习",
            description = "课程与校园效率工具",
            items = listOf(
                FunctionItemData(
                    id = "schedule",
                    title = "课表",
                    icon = Icons.Outlined.CalendarMonth,
                    tintColor = FunctionColors.Schedule,
                    onClick = {},
                ),
            ),
            modifier = Modifier.padding(16.dp),
        )
    }
}

private const val GRID_COLUMN_COUNT = 4
