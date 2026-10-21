package com.mychuannong.app.core.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Shared app-owned navigation control, independent of page accent colors. */
@Composable
fun AppBackButton(onBack: () -> Unit) {
    Surface(shape=CircleShape,color=AppTheme.colors.commonColor.copy(alpha = 0.08f)) {
        IconButton(onClick=onBack,modifier=Modifier.size(48.dp)) {
            Icon(androidx.compose.ui.res.painterResource(com.mychuannong.app.R.drawable.ic_secondary_back),"返回",modifier=Modifier.size(24.dp),tint=androidx.compose.ui.res.colorResource(com.mychuannong.app.R.color.widget_primary))
        }
    }
}

/** Own unconsumed top insets, including display cutouts; parent padding is not duplicated. */
@Composable
fun SecondaryPageHeader(title: String, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    androidx.compose.ui.layout.Layout(
        modifier = Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
            .padding(top = 10.dp, bottom = 18.dp),
        content = {
            Box { AppBackButton(onBack) }
            Text(title, color = AppTheme.colors.primaryTextColor,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, content = actions)
        },
    ) { children, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val back = children[0].measure(loose)
        val actionsPlaceable = children[2].measure(loose)
        val side = maxOf(back.width, actionsPlaceable.width) + 8.dp.roundToPx()
        val titlePlaceable = children[1].measure(loose.copy(maxWidth = (constraints.maxWidth - side * 2).coerceAtLeast(0)))
        val height = maxOf(back.height, actionsPlaceable.height, titlePlaceable.height)
        layout(constraints.maxWidth, height) {
            back.placeRelative(0, (height - back.height) / 2)
            titlePlaceable.placeRelative((constraints.maxWidth - titlePlaceable.width) / 2, (height - titlePlaceable.height) / 2)
            actionsPlaceable.placeRelative(constraints.maxWidth - actionsPlaceable.width, (height - actionsPlaceable.height) / 2)
        }
    }
}

@Composable
fun SecondaryCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
        color = AppTheme.colors.bgCardColor, shadowElevation = 1.dp) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), content = content)
    }
}

@Composable
fun SecondaryRow(title: String, value: String = "", onClick: (() -> Unit)? = null,
    selected: Boolean = false,
    control: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().heightIn(min = 58.dp)
        .clip(RoundedCornerShape(12.dp))
        .then(if (selected) Modifier.background(AppTheme.colors.commonColor.copy(alpha = 0.09f)) else Modifier)
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), fontSize = 16.sp, color = AppTheme.colors.primaryTextColor)
        if (control != null) control() else {
            if (value.isNotEmpty()) Text(value, color = AppTheme.colors.secondaryTextColor, fontSize = 14.sp)
            if (onClick != null) Icon(Icons.Rounded.ChevronRight, null, tint = AppTheme.colors.secondaryTextColor)
        }
    }
}

@Composable
fun BrandSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Switch(checked, onCheckedChange, colors = SwitchDefaults.colors(
        checkedTrackColor = AppTheme.colors.commonColor, checkedThumbColor = AppTheme.colors.bgCardColor,
        uncheckedTrackColor = AppTheme.colors.bgLightGrayColor,
        uncheckedThumbColor = AppTheme.colors.secondaryTextColor,
        uncheckedBorderColor = AppTheme.colors.controlDividerColor))
}

/** Scaffold top bars own the top safe area; body padding comes from Scaffold. */
@Composable
fun SecondaryScaffoldHeader(title: String, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    // Inherit the Scaffold canvas instead of introducing a separate header color band.
    Box(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp)) {
        SecondaryPageHeader(title, onBack, actions)
    }
}
