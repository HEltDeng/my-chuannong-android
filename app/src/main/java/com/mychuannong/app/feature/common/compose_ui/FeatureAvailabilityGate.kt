package com.mychuannong.app.feature.common.compose_ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mychuannong.app.core.network.campustools.FeatureAvailability
import com.mychuannong.app.core.network.campustools.RemoteConfigurationStore
import com.mychuannong.app.core.theme.AppTheme
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun FeatureAvailabilityGate(
    featureKey: String,
    title: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    val bootstrap by RemoteConfigurationStore.bootstrap.collectAsState()
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    // Derive availability from the collected value so a successful retry recomposes this gate.
    // Reading only the store singleton here would leave the screen stuck on stale maintenance data.
    val availability = remember(bootstrap, featureKey) {
        RemoteConfigurationStore.feature(featureKey)
    }
    LaunchedEffect(lifecycleOwner, featureKey) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                RemoteConfigurationStore.refreshIfStale(30_000L)
                delay(30_000L)
            }
        }
    }
    if (availability.isOpen) {
        content()
    } else {
        com.mychuannong.app.feature.common.ui.FeatureUnavailablePage(
            title = title,
            status = availability.status,
            message = availability.message,
            recoverAt = availability.expectedRestoreAt,
            onBack = onBack,
        )
    }
}

@Composable
fun FeatureAvailabilityNotice(
    title: String,
    availability: FeatureAvailability,
    modifier: Modifier = Modifier,
) {
    if (availability.isOpen) return
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = AppTheme.colors.bgButtonLowlightColor,
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "$title${availability.label}",
                color = AppTheme.colors.primaryTextColor,
                fontWeight = FontWeight.Bold,
            )
            Text(
                availabilityMessage(availability),
                color = AppTheme.colors.secondaryTextColor,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                modifier = Modifier.padding(top = 5.dp),
            )
        }
    }
}

@Composable
private fun FeatureUnavailableScreen(
    title: String,
    availability: FeatureAvailability,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppTheme.colors.bgPrimaryColor)
            .statusBarsPadding(),
    ) {
        com.mychuannong.app.core.theme.SecondaryScaffoldHeader(title,onBack)
        Column(
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .padding(28.dp),
        ) {
            Surface(
                color = AppTheme.colors.bgButtonLowlightColor,
                shape = RoundedCornerShape(22.dp),
            ) {
                Icon(
                    if (availability.status == "MAINTENANCE") Icons.Rounded.Build else Icons.Rounded.Schedule,
                    contentDescription = null,
                    tint = AppTheme.colors.functionalTextColor,
                    modifier = Modifier.padding(22.dp),
                )
            }
            Spacer(Modifier.padding(9.dp))
            Text(
                availability.label,
                color = AppTheme.colors.primaryTextColor,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                availabilityMessage(availability),
                color = AppTheme.colors.secondaryTextColor,
                fontSize = 14.sp,
                lineHeight = 21.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
            Button(
                onClick = onRetry,
                modifier = Modifier.padding(top = 18.dp),
            ) {
                Text("刷新状态")
            }
        }
    }
}

private fun availabilityMessage(availability: FeatureAvailability): String {
    val restore = availability.expectedRestoreAt?.let {
        "${availability.timeLabel}：${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it))}"
    }
    return listOfNotNull(
        availability.message.takeIf(String::isNotBlank)
            ?: availability.label,
        restore,
    ).joinToString("\n")
}
