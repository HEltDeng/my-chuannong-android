package com.mychuannong.app.core.privacy

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import org.json.JSONObject
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.annotation.StringRes
import com.mychuannong.app.R
import com.mychuannong.app.core.Route
import com.mychuannong.app.core.theme.AppSkinTheme
import com.mychuannong.app.core.theme.AppTheme

enum class ReleaseDocumentType(val value: String, @param:StringRes val titleRes: Int) {
    PRIVACY("privacy", R.string.release_document_title_privacy),
    AGREEMENT("agreement", R.string.release_document_title_agreement),
    THIRD_PARTY("third_party", R.string.release_document_title_third_party),
    PERSONAL_INFORMATION("personal_information", R.string.release_document_title_personal_information),
    SERVICE_INFORMATION("service_information", R.string.release_document_title_service_information),
    ACCOUNT_DATA("account_data", R.string.release_document_title_account_data);

    companion object {
        fun from(value: String?): ReleaseDocumentType =
            entries.firstOrNull { it.value == value } ?: PRIVACY
    }
}

private data class V1InformationSection(val title: String, val body: String)

class ReleaseInformationActivity : ComponentActivity() {
    companion object {
        private const val EXTRA_TYPE = "release_document_type"

        fun intent(context: Context, type: ReleaseDocumentType): Intent =
            Intent(context, ReleaseInformationActivity::class.java)
                .putExtra(EXTRA_TYPE, type.value)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val type = ReleaseDocumentType.from(intent.getStringExtra(EXTRA_TYPE))
        setContent { AppSkinTheme { V1InformationScreen(this, type) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun V1InformationScreen(activity: ComponentActivity, type: ReleaseDocumentType) {
    val scope = rememberCoroutineScope()
    val key = when (type) {
        ReleaseDocumentType.PRIVACY -> "privacy"
        ReleaseDocumentType.AGREEMENT -> "agreement"
        ReleaseDocumentType.PERSONAL_INFORMATION -> "collection"
        ReleaseDocumentType.THIRD_PARTY -> "thirdparties"
        else -> null
    }
    val context = LocalContext.current
    val document = remember(key) { key?.let { readLegalDocument(context, it) } }
    val title = document?.firstOrNull()?.takeIf { it.kind == "title" }?.text?.text
        ?: stringResource(type.titleRes)
    Scaffold(
        containerColor = AppTheme.colors.overviewPageBackgroundColor,
        topBar = {
            com.mychuannong.app.core.theme.SecondaryScaffoldHeader(title, activity::finish)
        },
    ) { paddingValues ->
        if (key != null) {
            if (document.isNullOrEmpty()) {
                Text("协议正文无法读取。", modifier = Modifier.padding(paddingValues).padding(20.dp))
            } else {
                LegalDocumentBody(document, type, Modifier.padding(paddingValues))
            }
            return@Scaffold
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxSize()
                .background(AppTheme.colors.overviewPageBackgroundColor)
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            v1InformationSections(type).forEach { section ->
                Text(
                    text = section.title,
                    color = AppTheme.colors.primaryTextColor,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = section.body,
                    color = AppTheme.colors.secondaryTextColor,
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                )
            }
            if (type == ReleaseDocumentType.ACCOUNT_DATA) {
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = { Route.goAccountCenter(activity) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("管理我的川农账号") }
                OutlinedButton(
                    onClick = { scope.launch { AccountDataManager.clearSchoolBinding() } },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("解除学校账号绑定并清理会话") }
                OutlinedButton(
                    onClick = { scope.launch { AccountDataManager.clearLocalLearningData() } },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("清除本地课表与学习缓存") }
            }
            Text(
                text = "我的川农 · 协议版本 ${PrivacyConsentManager.POLICY_VERSION}",
                color = AppTheme.colors.greyTextColor,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 10.dp, bottom = 28.dp),
            )
        }
    }
}

private data class LegalParagraph(val kind: String, val text: AnnotatedString)

private fun readLegalDocument(context: Context, key: String): List<LegalParagraph> = runCatching {
    val json = context.assets.open("MyChuannongLegal.json").bufferedReader().use { JSONObject(it.readText()) }
    require(json.getString("version") == PrivacyConsentManager.POLICY_VERSION)
    val paragraphs = json.getJSONObject("documents").getJSONArray(key)
    require(paragraphs.length() > 0)
    List(paragraphs.length()) { index ->
        val paragraph = paragraphs.getJSONObject(index)
        val runs = paragraph.getJSONArray("runs")
        val text = buildAnnotatedString {
            for (i in 0 until runs.length()) {
                val run = runs.getJSONObject(i)
                val style = if (run.optBoolean("bold")) SpanStyle(fontWeight = FontWeight.Bold) else SpanStyle()
                withStyle(style) {
                    if (run.has("url")) {
                        withLink(LinkAnnotation.Url(run.getString("url"))) { append(run.getString("text")) }
                    } else {
                        append(run.getString("text"))
                    }
                }
            }
        }
        LegalParagraph(paragraph.getString("kind"), text)
    }
}.getOrDefault(emptyList())

@Composable
private fun LegalDocumentBody(paragraphs: List<LegalParagraph>, type: ReleaseDocumentType, modifier: Modifier) {
    val context = LocalContext.current
    SelectionContainer {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        ) {
            Text("本版服务范围", color = AppTheme.colors.primaryTextColor, fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
            Text(ANDROID_SERVICE_SCOPE, color = AppTheme.colors.primaryTextColor, fontSize = 16.sp, lineHeight = 25.sp)
            val body = if (paragraphs.firstOrNull()?.kind == "title") paragraphs.drop(1) else paragraphs
            for (paragraph in body) {
                val isHeading = paragraph.kind == "heading" || paragraph.kind == "subheading"
                Text(
                    text = paragraph.text,
                    color = AppTheme.colors.primaryTextColor,
                    fontSize = if (paragraph.kind == "heading") 20.sp else 16.sp,
                    fontWeight = if (isHeading) FontWeight.SemiBold else FontWeight.Normal,
                    lineHeight = 25.sp,
                    modifier = if (isHeading) Modifier.padding(top = 6.dp).semantics { heading() } else Modifier,
                )
            }
            Text("相关文件", color = AppTheme.colors.primaryTextColor, fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp).semantics { heading() })
            ReleaseDocumentType.entries.filter { it != type && it != ReleaseDocumentType.ACCOUNT_DATA }.forEach { related ->
                TextButton(onClick = { context.startActivity(ReleaseInformationActivity.intent(context, related)) },
                    modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(related.titleRes), color = AppTheme.colors.functionalTextColor)
                }
            }
            Text("协议版本 ${PrivacyConsentManager.POLICY_VERSION}", color = AppTheme.colors.secondaryTextColor,
                fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp, bottom = 16.dp))
        }
    }
}

private const val ANDROID_SERVICE_SCOPE =
    "本 Android 版本不提供校园地图或平台短信功能，不加载高德地图 SDK。平台账号使用手机号和密码注册、登录，不将手机号标记为短信验证通过。学校要求的验证码和二次验证仍由本人完成。"

private fun v1InformationSections(type: ReleaseDocumentType): List<V1InformationSection> =
    when (type) {
        ReleaseDocumentType.ACCOUNT_DATA -> listOf(
            V1InformationSection(
                "我的川农账号",
                "账号中心提供登录状态管理、退出和后端已支持的账号数据操作；服务未配置时不会假装操作成功。",
            ),
            V1InformationSection(
                "学校账号解绑",
                "用户可单独解除学校账号绑定并清除学校凭据、Cookie/session 和校园查询缓存。该操作不清除“我的川农”自有账号。",
            ),
            V1InformationSection(
                "本地学习数据",
                "用户可清除本地课表、课程提醒数据和学习缓存。",
            ),
        )
        ReleaseDocumentType.SERVICE_INFORMATION -> listOf(
            V1InformationSection("本版服务范围", ANDROID_SERVICE_SCOPE),
            V1InformationSection("学校账号与学习数据", "只需登录一次川农 e，应用通过学校 VPN 自动完成教务统一认证与学号核验，无需另填教务密码。川农 e 学号、密码与学校会话使用 Android Keystore 加密，保存在不可备份的本机目录，用于自动登录和刷新；学校要求验证码或二次验证时由本人完成，完成后继续刷新，解绑时清除。学校密码、验证码、Cookie、令牌、课表、成绩和考试不发送到我的川农后台。网络或学校服务不可用时保留已保存的学习缓存。"),
            V1InformationSection("平台账号", "我的川农账号用于资料库、校园社区、机构预约和意见反馈，与学校账号分别管理。注册时填写手机号、密码并确认密码；手机号仅关联本人使用的账号，不代表已完成短信验证或人工身份核验。"),
            V1InformationSection("跑腿代取", "跑腿代取暂未开放。开放时展示实际服务提供方、服务内容及联系入口。"),
            V1InformationSection("资料与社区", "资料库提供外部链接；社区发布按平台规则审核。可举报社区内容，或在本机屏蔽发布者；屏蔽名单可在“我的 → 隐私与合规”中管理。使用公开联系方式时请尊重他人隐私。"),
            V1InformationSection("服务开放状态", "板块开放状态由超级管理员设置，维护、关闭或即将上线时暂停对应业务；预计时间以实际开放状态为准。"),
        )
        else -> emptyList()
    }
