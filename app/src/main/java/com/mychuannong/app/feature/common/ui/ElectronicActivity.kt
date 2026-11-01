package com.mychuannong.app.feature.common.ui

import com.mychuannong.app.feature.common.compose_ui.FeatureAvailabilityGate
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mychuannong.app.R
import com.mychuannong.app.base.ComposeActivity
import com.mychuannong.app.core.theme.AppBackButton
import com.mychuannong.app.core.theme.AppTheme

/** WeChat-only service guide. No provider session, account or payment handling. */
class ElectronicActivity : ComposeActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setComposeContent {
            FeatureAvailabilityGate("electric", "电费充值", ::finish) {
                ElectricityGuide(::finish, ::copyAccountName)
            }
        }
    }

    private fun copyAccountName() {
        (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
            .setPrimaryClip(ClipData.newPlainText("公众号名称", "川农后勤"))
        Toast.makeText(this, "已复制公众号名称", Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun ElectricityGuide(onBack: () -> Unit, onCopy: () -> Unit) {
    val colors = AppTheme.colors
    Column(Modifier.fillMaxSize().background(colors.bgPrimaryColor).statusBarsPadding().navigationBarsPadding()) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Box(Modifier.align(Alignment.TopStart)) { AppBackButton(onBack) }
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("电费查询", fontSize = 23.sp, fontWeight = FontWeight.Bold, color = colors.primaryTextColor)
                Text("请在手机微信内完成查询", fontSize = 13.sp, color = colors.secondaryTextColor,
                    modifier = Modifier.padding(top = 4.dp).alpha(0f).clearAndSetSemantics {})
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            ManagedServiceGuide("electric")
            Surface(shape = RoundedCornerShape(10.dp), color = colors.bgTopBarColor) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("微信搜索公众号", fontSize = 13.sp, color = colors.secondaryTextColor)
                        Text("「川农后勤」", fontSize = 22.sp, fontWeight = FontWeight.Bold,
                            color = colors.commonColor, modifier = Modifier.padding(top = 6.dp))
                    }
                    OutlinedButton(onClick = onCopy, shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)) {
                        Text("复制公众号名称", fontSize = 12.sp, color = colors.commonColor)
                    }
                }
            }
            GuideHeading("查询路径")
            Column {
                GuideStep("1", "搜索公众号", "在微信搜索「川农后勤」并进入公众号")
                HorizontalDivider(Modifier.padding(vertical = 16.dp), color = colors.outlineLowContrastColor)
                GuideStep("2", "打开电精灵", "底部菜单「智慧后勤」→「电精灵」")
                HorizontalDivider(Modifier.padding(vertical = 16.dp), color = colors.outlineLowContrastColor)
                GuideStep("3", "进入水电管理", "在平台首页点击「水电管理」")
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                GuideHeading("入口示意")
                Text("进入「电精灵」后，点击下方「水电管理」", fontSize = 13.sp, color = colors.secondaryTextColor)
                // Display only the illustration viewport from the supplied reference, not its UI buttons.
                val reference = ImageBitmap.imageResource(R.drawable.electric_guide_reference)
                Canvas(Modifier.fillMaxWidth().aspectRatio(783f / 485f).clip(RoundedCornerShape(12.dp))
                    .semantics { contentDescription = "电精灵入口示意图，绿色框标出水电管理；此图仅供参考" }) {
                    drawImage(reference, srcOffset = IntOffset(35, 1070), srcSize = IntSize(783, 485),
                        dstSize = IntSize(size.width.toInt(), size.height.toInt()))
                }
            }
            Text(buildAnnotatedString {
                append("复制后请打开微信，")
                withStyle(SpanStyle(color = Color.Transparent)) { append("搜索并关注") }
                append("公众号完成查询")
            }, fontSize = 12.sp,
                color = colors.secondaryTextColor, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = "复制后请打开微信，公众号完成查询" })
        }
    }
}

@Composable
private fun GuideHeading(title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.width(4.dp).height(20.dp).clip(RoundedCornerShape(2.dp)).background(AppTheme.colors.commonColor))
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = AppTheme.colors.primaryTextColor)
    }
}

@Composable
private fun GuideStep(number: String, title: String, description: String) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(38.dp).background(AppTheme.colors.bgTopBarColor, CircleShape), contentAlignment = Alignment.Center) {
            Text(number, fontSize = 23.sp, fontWeight = FontWeight.Bold, color = AppTheme.colors.commonColor)
        }
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = AppTheme.colors.primaryTextColor)
            Text(description, fontSize = 14.sp, lineHeight = 21.sp, color = AppTheme.colors.secondaryTextColor)
        }
    }
}
