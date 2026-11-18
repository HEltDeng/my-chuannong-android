package com.mychuannong.app.core.privacy

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mychuannong.app.R
import com.mychuannong.app.core.theme.AppTheme

const val NON_OFFICIAL_STATEMENT =
    "本应用由公司独立运营，是面向校园生活场景的非官方辅助工具，与四川农业大学不存在官方隶属或授权关系。"

/** Both entry points open the same bundled documents used by 我的 → 隐私与合规. */
@Composable
fun PrivacyConsentScreen(
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenAgreement: () -> Unit,
) {
    val green = AppTheme.colors.functionalTextColor
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.White)) {
        val maxDialogHeight = maxHeight * 0.88f
        Column(
            Modifier.fillMaxSize().padding(32.dp).clearAndSetSemantics {},
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
            Image(painterResource(R.drawable.my_chuannong_brand_logo), null, Modifier.size(76.dp))
            Spacer(Modifier.height(18.dp))
            Text("欢迎使用我的川农", color = AppTheme.colors.primaryTextColor, fontSize = 23.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Image(painterResource(R.drawable.my_chuannong_brand_logo), null, Modifier.size(28.dp))
                Text("我的川农", color = green, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
        }
        Dialog(
            onDismissRequest = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false, usePlatformDefaultWidth = false),
        ) {
            Surface(
                modifier = Modifier.widthIn(max = 496.dp).fillMaxWidth().padding(horizontal = 38.dp)
                    .heightIn(max = maxDialogHeight),
                shape = RoundedCornerShape(24.dp),
                color = Color.White,
            ) {
                Column {
                    Text(
                        "用户协议及隐私保护", fontSize = 18.sp, fontWeight = FontWeight.Bold,
                        color = AppTheme.colors.primaryTextColor, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 18.dp)
                            .semantics { heading() },
                    )
                    Column(
                        Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("请阅读以下文件。点击“同意”后进入我的川农。", color = AppTheme.colors.primaryTextColor,
                            fontSize = 15.sp, lineHeight = 23.sp)
                        ConsentDocumentLink("用户协议", onOpenAgreement)
                        ConsentDocumentLink("隐私政策", onOpenPrivacy)
                    }
                    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Button(onClick = onAccept, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp), shape = RoundedCornerShape(50), colors = ButtonDefaults.buttonColors(containerColor = green)) {
                            Text("同意", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        }
                        TextButton(onClick = onDecline, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)) {
                            Text("不同意", color = Color(0xFF666666), fontSize = 15.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConsentDocumentLink(title: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Outlined.Description, contentDescription = null, tint = AppTheme.colors.functionalTextColor)
            Text(title, color = AppTheme.colors.functionalTextColor, fontSize = 16.sp, modifier = Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = AppTheme.colors.functionalTextColor)
        }
    }
}
