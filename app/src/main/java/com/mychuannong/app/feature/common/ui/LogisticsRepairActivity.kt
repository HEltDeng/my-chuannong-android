package com.mychuannong.app.feature.common.ui

import com.mychuannong.app.feature.common.compose_ui.FeatureAvailabilityGate
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mychuannong.app.R
import com.mychuannong.app.base.ComposeActivity
import com.mychuannong.app.core.theme.AppTheme
import com.mychuannong.app.core.theme.SecondaryScaffoldHeader

class LogisticsRepairActivity : ComposeActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setComposeContent {
            FeatureAvailabilityGate("logistics_repair", "后勤报修", ::finish) {
                RepairGuide(::finish) {
                    (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                        .setPrimaryClip(ClipData.newPlainText("公众号名称", "川农后勤"))
                    Toast.makeText(this, "已复制公众号名称", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

@Composable
private fun RepairGuide(onBack: () -> Unit, onCopy: () -> Unit) {
    val colors = AppTheme.colors
    Scaffold(containerColor = colors.bgPrimaryColor,
        topBar = { SecondaryScaffoldHeader("后勤报修", onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            ManagedServiceGuide("logistics_repair")
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
            RepairHeading("报修路径")
            Column {
                RepairStep("1", "搜索公众号", "在微信搜索「川农后勤」并进入公众号")
                HorizontalDivider(Modifier.padding(vertical = 16.dp), color = colors.outlineLowContrastColor)
                RepairStep("2", "打开报修平台", "底部菜单「智慧后勤」→「后勤报修」")
                HorizontalDivider(Modifier.padding(vertical = 16.dp), color = colors.outlineLowContrastColor)
                RepairStep("3", "发起报修", "在平台首页点击「我要报修」")
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RepairHeading("入口示意")
                Text("进入「后勤报修」后，点击「我要报修」", fontSize = 13.sp, color = colors.secondaryTextColor)
                val reference = androidx.compose.ui.graphics.ImageBitmap.imageResource(R.drawable.logistics_repair_reference)
                // The supplied image remains intact; display only its entrance illustration viewport.
                androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().aspectRatio(777f / 470f)
                    .clip(RoundedCornerShape(12.dp))
                    .semantics { contentDescription = "后勤报修入口示意图，绿色框标出我要报修；此图仅供参考" }) {
                    drawImage(reference, srcOffset = androidx.compose.ui.unit.IntOffset(32, 1252),
                        srcSize = androidx.compose.ui.unit.IntSize(777, 470),
                        dstSize = androidx.compose.ui.unit.IntSize(size.width.toInt(), size.height.toInt()))
                }
            }
        }
    }
}

@Composable
private fun RepairHeading(title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.width(4.dp).height(20.dp).clip(RoundedCornerShape(2.dp)).background(AppTheme.colors.commonColor))
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = AppTheme.colors.primaryTextColor)
    }
}

@Composable
private fun RepairStep(number: String, title: String, description: String) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(38.dp).background(AppTheme.colors.bgTopBarColor, CircleShape), contentAlignment = Alignment.Center) {
            Text(number, fontSize = 23.sp, fontWeight = FontWeight.Bold, color = AppTheme.colors.commonColor)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = AppTheme.colors.primaryTextColor)
            Text(description, fontSize = 14.sp, lineHeight = 21.sp, color = AppTheme.colors.secondaryTextColor)
        }
    }
}
