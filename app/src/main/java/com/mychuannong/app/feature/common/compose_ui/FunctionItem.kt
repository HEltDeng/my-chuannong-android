package com.mychuannong.app.feature.common.compose_ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import com.mychuannong.app.core.theme.AppSkinTheme
import com.mychuannong.app.core.theme.AppTheme

@Immutable
data class FunctionItemData(
    val id: String,
    val title: String,
    val icon: ImageVector,
    val tintColor: Color = Color.Unspecified,
    val statusLabel: String? = null,
    val onClick: () -> Unit,
)

@Composable
fun FunctionItem(
    item: FunctionItemData,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = tween(durationMillis = 90),
        label = "functionItemScale",
    )
    val accessibilityLabel = item.title

    Column(
        modifier = modifier
            .scale(scale)
            .heightIn(min = 88.dp)
            .clip(RoundedCornerShape(16.dp))
            .semantics(mergeDescendants = true) {
                contentDescription = accessibilityLabel
            }
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                role = Role.Button,
                onClick = item.onClick,
            )
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(item.tintColor.copy(alpha = 0.11f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                modifier = Modifier.size(25.dp),
                tint = item.tintColor,
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = item.title,
            color = AppTheme.colors.primaryTextColor,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )

    }
}

@Preview(showBackground = true)
@Composable
private fun FunctionItemPreview() {
    AppSkinTheme {
        FunctionItem(
            item = FunctionItemData(
                id = "schedule",
                title = "课表",
                icon = Icons.Outlined.CalendarMonth,
                tintColor = FunctionColors.Schedule,
                onClick = {},
            ),
        )
    }
}
