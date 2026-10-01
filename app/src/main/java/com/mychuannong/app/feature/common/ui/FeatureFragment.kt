package com.mychuannong.app.feature.common.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import com.mychuannong.app.R
import com.mychuannong.app.BuildConfig
import com.mychuannong.app.core.theme.AppSkinTheme
import com.mychuannong.app.core.theme.AppTheme
import com.mychuannong.app.core.network.campustools.RemoteConfigurationStore


import com.mychuannong.app.core.network.campustools.RemoteServiceUpdate
import com.mychuannong.app.feature.common.compose_ui.FunctionSection
import com.mychuannong.app.feature.common.compose_ui.ServiceDynamicsSection
import com.mychuannong.app.feature.common.compose_ui.filterServiceUpdatesForClient
import com.mychuannong.app.feature.common.compose_ui.learningFunctionShortcuts
import com.mychuannong.app.feature.common.compose_ui.lifeFunctionShortcuts
import com.mychuannong.app.feature.common.compose_ui.toFunctionItemData
import com.mychuannong.app.profileSettings.ui.AboutActivity
import com.mychuannong.app.feature.common.ui.AnnouncementDetailActivity
import com.mychuannong.app.feature.common.ui.FeatureUnavailableActivity
import com.mychuannong.app.widget.view.CustomToast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

class FeatureFragment : Fragment() {
    companion object {
        @JvmStatic
        fun newInstance() = FeatureFragment()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            AppSkinTheme {
                FeatureScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeatureScreen(
    modifier: Modifier = Modifier,
    isActive: Boolean = true,
) {
    val context = LocalContext.current
    val appConfigState by RemoteConfigurationStore.appConfig.collectAsState()
    val serviceUpdates by RemoteConfigurationStore.serviceUpdates.collectAsState()
    val isRefreshing by RemoteConfigurationStore.serviceUpdatesRefreshing.collectAsState()
    val clientUpdates = remember(serviceUpdates) {
        filterServiceUpdatesForClient(serviceUpdates, BuildConfig.VERSION_CODE)
    }
    val scope = rememberCoroutineScope()
    var dialogUpdate by remember { mutableStateOf<RemoteServiceUpdate?>(null) }
    val learningItems = remember(context, appConfigState.envelope.configVersion) {
        learningFunctionShortcuts(appConfigState.envelope.config).map { it.toFunctionItemData(context) }
    }
    val lifeItems = remember(context, appConfigState.envelope.configVersion) {
        lifeFunctionShortcuts(appConfigState.envelope.config).map { it.toFunctionItemData(context) }
    }

    LaunchedEffect(isActive) {
        if (!isActive) return@LaunchedEffect
        runCatching { RemoteConfigurationStore.refresh() }
        runCatching { RemoteConfigurationStore.refreshServiceUpdates() }
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            scope.launch {
                runCatching { RemoteConfigurationStore.refreshServiceUpdates() }
                    .onFailure { CustomToast.showMessage(context, "刷新失败，继续显示已有内容") }
            }
        },
        modifier = modifier
            .fillMaxSize()
            .background(AppTheme.colors.overviewPageBackgroundColor),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding(),
        ) {
            FeatureHeaderSection()

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                if (clientUpdates.isNotEmpty()) {
                    ServiceDynamicsSection(
                        updates = clientUpdates,
                        onUpdateClick = { update ->
                            when (update.targetType) {
                                "ABOUT_UPDATE" -> {
                                    context.startActivity(
                                        Intent(context, AboutActivity::class.java)
                                            .putExtra(AboutActivity.EXTRA_AUTO_CHECK_UPDATE, true),
                                    )
                                }
                                "FEATURE" -> {
                                    val moduleLabel = when (update.moduleKey) {
                                        "lost_found" -> "失物招领"
                                        "resource_library" -> "资料库"
                                        else -> "该服务"
                                    }
                                    context.startActivity(
                                        FeatureUnavailableActivity.intent(
                                            context = context,
                                            title = update.title.ifBlank {
                                                "$moduleLabel${if (update.type == "MAINTENANCE") "维护中" else "暂不开放"}"
                                            },
                                            status = update.type,
                                            message = update.summary,
                                            recoverAt = update.recoverAt,
                                        ),
                                    )
                                }
                                "ANNOUNCEMENT" -> {
                                    context.startActivity(
                                        AnnouncementDetailActivity.intent(
                                            context = context,
                                            title = update.title,
                                            content = update.summary,
                                            publishedAt = update.publishedAt,
                                        ),
                                    )
                                }
                                else -> dialogUpdate = update
                            }
                        },
                        modifier = Modifier.padding(bottom = 14.dp),
                    )
                }
                FunctionSection(
                    title = stringResource(R.string.feature_section_learning),
                    description = stringResource(R.string.feature_section_learning_description),
                    items = learningItems,
                )
                FunctionSection(
                    title = stringResource(R.string.feature_section_life),
                    description = stringResource(R.string.feature_section_life_description),
                    items = lifeItems,
                )
                FunctionSection(title = "学校服务", description = "学校官方网站与公共服务", items = com.mychuannong.app.school.SchoolServiceCatalog.items(context))
                Spacer(modifier = Modifier.height(112.dp))
            }
        }
    }
}

@Composable
private fun ServiceUpdateDetailDialog(
    update: RemoteServiceUpdate,
    onDismiss: () -> Unit,
) {
    val colors = AppTheme.colors
    val restoreText = update.recoverAt?.let {
        "预计恢复：${SimpleDateFormat("M月d日 HH:mm", Locale.CHINA).format(Date(it))}"
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(update.title, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    text = update.summary,
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                )
                if (!restoreText.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = restoreText,
                        fontSize = 13.sp,
                        color = colors.greyTextColor,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("知道了") }
        },
    )
}
@Composable
private fun FeatureHeaderSection() {
    val colors = AppTheme.colors

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        colors.bgCardColor,
                        colors.commonColor.copy(alpha = 0.14f),
                    ),
                ),
            )
            .border(
                border = BorderStroke(
                    width = 1.dp,
                    color = colors.outlineLowContrastColor.copy(alpha = 0.34f),
                ),
                shape = RoundedCornerShape(24.dp),
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 132.dp)
                .padding(horizontal = 20.dp, vertical = 20.dp),
        ) {
            Text(
                text = stringResource(R.string.feature_header_title),
                color = colors.primaryTextColor,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.feature_header_description),
                color = colors.secondaryTextColor,
                fontSize = 14.sp,
                lineHeight = 21.sp,
            )
        }


    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun FeatureScreenPreview() {
    AppSkinTheme {
        FeatureScreen()
    }
}
