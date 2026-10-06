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

/** Own unconsumed top insets, including display cutouts; parent padding is not duplicated. */
@Composable
fun SecondaryPageHeader(title: String, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
        .padding(top = 10.dp, bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = CircleShape, color = AppTheme.colors.bgCardColor) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Rounded.ArrowBack, "返回", tint = AppTheme.colors.iconPrimaryColor)
            }
        }
        Text(title, Modifier.weight(1f).padding(start = 12.dp), color = AppTheme.colors.primaryTextColor,
            fontSize = 24.sp, fontWeight = FontWeight.Bold)
        actions()
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
    Box(Modifier.fillMaxWidth().background(AppTheme.colors.overviewPageBackgroundColor).statusBarsPadding().padding(horizontal = 16.dp)) {
        SecondaryPageHeader(title, onBack, actions)
    }
}
