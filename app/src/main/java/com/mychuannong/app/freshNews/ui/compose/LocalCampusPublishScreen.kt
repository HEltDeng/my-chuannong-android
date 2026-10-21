package com.mychuannong.app.freshNews.ui.compose

import android.view.inputmethod.EditorInfo
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalFocusManager
import com.mychuannong.app.core.theme.AppTheme
import com.mychuannong.app.feature.common.compose_ui.ImeCompatibleTextField
import com.mychuannong.app.freshNews.data.local.model.LocalCampusPostRules
import com.mychuannong.app.freshNews.viewModel.LocalCampusPublishUiState
import com.mychuannong.app.freshNews.viewModel.LocalCampusPublishViewModel

@Composable
fun LocalCampusPublishScreen(
    viewModel: LocalCampusPublishViewModel,
    onClose: () -> Unit,
    onPublished: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    LocalCampusPublishContent(
        state = state,
        onTitleChange = viewModel::updateTitle,
        onContentChange = viewModel::updateContent,
        onTagSelected = viewModel::updateTag,
        onKeepDraft = viewModel::keepDraft,
        onDiscardDraft = viewModel::discardDraft,
        onClose = onClose,
        onPublish = {
            if (viewModel.publish()) onPublished()
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocalCampusPublishContent(
    state: LocalCampusPublishUiState,
    onTitleChange: (String) -> Unit,
    onContentChange: (String) -> Unit,
    onTagSelected: (String) -> Unit,
    onKeepDraft: () -> Boolean,
    onDiscardDraft: () -> Unit,
    onClose: () -> Unit,
    onPublish: () -> Unit
) {
    val colors = AppTheme.colors
    val focusManager = LocalFocusManager.current
    var showExitDialog by remember { mutableStateOf(false) }

    fun requestClose() {
        focusManager.clearFocus()
        if (state.hasContent) showExitDialog = true else onClose()
    }

    BackHandler(onBack = ::requestClose)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = colors.overviewPageBackgroundColor,
        topBar = {
            com.mychuannong.app.core.theme.SecondaryScaffoldHeader("发布帖子",::requestClose)
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.overviewPageBackgroundColor)
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Text(
                text = "标题（可选）",
                color = colors.primaryTextColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            ImeCompatibleTextField(
                value = state.title,
                onValueChange = onTitleChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = "用一句话概括内容",
                helperText = "${state.title.length}/${LocalCampusPostRules.MAX_TITLE_LENGTH}"
            )

            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = "正文",
                color = colors.primaryTextColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            ImeCompatibleTextField(
                value = state.content,
                onValueChange = onContentChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 190.dp),
                placeholder = "分享校园生活、求助或经验……",
                helperText = if (state.content.isBlank()) {
                    "正文必填 · 0/${LocalCampusPostRules.MAX_CONTENT_LENGTH}"
                } else {
                    "${state.content.length}/${LocalCampusPostRules.MAX_CONTENT_LENGTH}"
                },
                singleLine = false,
                minLines = 7,
                maxLines = 14,
                imeAction = EditorInfo.IME_ACTION_NONE
            )

            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "选择话题",
                color = colors.primaryTextColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LocalCampusPostRules.tags.forEach { tag ->
                    FilterChip(
                        selected = state.tag == tag,
                        onClick = { onTagSelected(tag) },
                        label = { Text(tag) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(22.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = colors.bgCardColor
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Info,
                        contentDescription = null,
                        tint = colors.commonColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.size(10.dp))
                    Text(
                        text = "请遵守校园社区规范，不发布违法、侵权、欺诈或个人隐私内容。本地版本不会公开传播，也不代表已提供线上审核。",
                        color = colors.secondaryTextColor,
                        fontSize = 13.sp,
                        lineHeight = 20.sp
                    )
                }
            }

            state.storageError?.let { error ->
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = error,
                    color = Color(0xFFB3261E),
                    fontSize = 13.sp
                )
            }

            Spacer(modifier = Modifier.height(22.dp))
            Button(
                onClick = {
                    focusManager.clearFocus()
                    onPublish()
                },
                enabled = state.canPublish,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.commonColor,
                    contentColor = colors.textButtonColor,
                    disabledContainerColor = colors.bgButtonLowlightColor,
                    disabledContentColor = colors.secondaryTextColor
                )
            ) {
                Text(
                    text = "发布到本机",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("保留未发布内容？") },
            text = { Text("草稿只保存在这台设备，清除 App 数据或卸载后将消失。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (onKeepDraft()) {
                            showExitDialog = false
                            onClose()
                        }
                    }
                ) {
                    Text("保留草稿")
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            onDiscardDraft()
                            showExitDialog = false
                            onClose()
                        }
                    ) {
                        Text("放弃")
                    }
                    TextButton(onClick = { showExitDialog = false }) {
                        Text("继续编辑")
                    }
                }
            },
            containerColor = colors.bgCardColor,
            titleContentColor = colors.primaryTextColor,
            textContentColor = colors.secondaryTextColor
        )
    }
}
