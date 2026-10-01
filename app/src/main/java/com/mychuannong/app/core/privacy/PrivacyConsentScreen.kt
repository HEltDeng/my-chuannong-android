package com.mychuannong.app.core.privacy

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mychuannong.app.R
import com.mychuannong.app.core.theme.AppTheme

const val NON_OFFICIAL_STATEMENT =
    "本应用由个人开发者独立开发，是面向校园生活场景的非官方辅助工具，与四川农业大学不存在官方隶属或授权关系。"

@Composable
fun PrivacyConsentScreen(
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenAgreement: () -> Unit,
) {
    val brandShape = RoundedCornerShape(26.dp)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
        modifier = Modifier
            .fillMaxSize()
            .background(AppTheme.colors.bgPrimaryColor)
            .statusBarsPadding()
            .padding(horizontal = 28.dp, vertical = 24.dp),
    ) {
        Spacer(modifier = Modifier.height(40.dp))
        Box(
            modifier = Modifier
                .size(104.dp)
                .dropShadow(
                    shape = brandShape,
                    shadow = Shadow(radius = 14.dp, spread = 1.dp,
                        color = Color(0x387B9FCB), offset = DpOffset(0.dp, 6.dp)),
                ),
        ) {
            Image(
                painter = painterResource(R.drawable.my_chuannong_brand_logo),
                contentDescription = "我的川农",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(brandShape),
            )
        }
        Text(
            text = "欢迎使用我的川农",
            color = AppTheme.colors.primaryTextColor,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 24.dp),
        )
        Text(
            text = "一款面向校园生活的实用工具",
            color = AppTheme.colors.secondaryTextColor,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        Spacer(modifier = Modifier.weight(1f))
        Button(
            onClick = onAccept,
            colors = ButtonDefaults.buttonColors(
                containerColor = AppTheme.colors.functionalTextColor,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 22.dp)
                .height(52.dp),
            shape = RoundedCornerShape(18.dp),
        ) { Text("同意并继续", fontWeight = FontWeight.Bold) }
        OutlinedButton(
            onClick = onDecline,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
                .height(52.dp),
            shape = RoundedCornerShape(18.dp),
        ) { Text("暂不同意") }
        Text(
            text = "继续使用即表示你已阅读并同意《隐私政策》和《用户协议》。",
            color = AppTheme.colors.secondaryTextColor,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            maxLines = 2,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 26.dp),
        )
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            TextButton(onClick = onOpenPrivacy) { Text("隐私政策") }
            TextButton(onClick = onOpenAgreement) { Text("用户协议") }
        }
    }
}

@Composable
fun PrivacyDeclinedScreen(
    onReviewDecision: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenAgreement: () -> Unit,
    onExit: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(AppTheme.colors.bgPrimaryColor)
            .statusBarsPadding()
            .padding(28.dp),
    ) {
        Text(
            text = "已选择暂不同意",
            color = AppTheme.colors.primaryTextColor,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "应用不会启动非必要联网或个人信息功能。你可以查看本地说明、重新选择，或退出应用。",
            color = AppTheme.colors.secondaryTextColor,
            fontSize = 15.sp,
            lineHeight = 22.sp,
            modifier = Modifier.padding(top = 12.dp, bottom = 20.dp),
        )
        Button(onClick = onReviewDecision, modifier = Modifier.fillMaxWidth()) {
            Text("重新选择")
        }
        Row(modifier = Modifier.padding(top = 8.dp)) {
            TextButton(onClick = onOpenPrivacy) { Text("隐私政策") }
            TextButton(onClick = onOpenAgreement) { Text("用户协议") }
        }
        TextButton(onClick = onExit) { Text("退出应用") }
    }
}
