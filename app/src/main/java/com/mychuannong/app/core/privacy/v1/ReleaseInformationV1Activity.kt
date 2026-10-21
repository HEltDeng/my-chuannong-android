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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import com.mychuannong.app.school.CampusAmapConsent
import androidx.compose.ui.res.painterResource
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
    val uriHandler = LocalUriHandler.current
    Scaffold(
        containerColor = AppTheme.colors.overviewPageBackgroundColor,
        topBar = {
            com.mychuannong.app.core.theme.SecondaryScaffoldHeader(stringResource(type.titleRes), activity::finish)
        },
    ) { paddingValues ->
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxSize()
                .background(AppTheme.colors.overviewPageBackgroundColor)
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            if (type == ReleaseDocumentType.PRIVACY) {
                Surface(
                    color = AppTheme.colors.bgCardColor,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = NON_OFFICIAL_STATEMENT,
                        color = AppTheme.colors.primaryTextColor,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(18.dp),
                    )
                }
            }
            v1InformationSections(
                type = type,
                androidComponentsDescription = stringResource(
                    R.string.release_third_party_android_components,
                ),
            ).forEach { section ->
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
            if(type == ReleaseDocumentType.PRIVACY || type == ReleaseDocumentType.THIRD_PARTY) {
                OutlinedButton(onClick={ uriHandler.openUri(CampusAmapConsent.PRIVACY_URL) },modifier=Modifier.fillMaxWidth()) {
                    Text("查看高德开放平台隐私政策")
                }
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
                text = stringResource(R.string.release_document_footer),
                color = AppTheme.colors.greyTextColor,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 10.dp, bottom = 28.dp),
            )
        }
    }
}

private fun v1InformationSections(
    type: ReleaseDocumentType,
    androidComponentsDescription: String,
): List<V1InformationSection> =
    when (type) {
        ReleaseDocumentType.PRIVACY -> listOf(
            V1InformationSection("高德校园地图", AMAP_DISCLOSURE),
            V1InformationSection(
                "学校账号与校园查询",
                "首次使用先在官方校园 VPN 页面完成川农 e 认证并检查校内服务，再以独立教务账号登录。两套会话默认分别保存和复用，优先使用学校返回的有效会话。教务登录验证成功后，默认将教务账号密码使用 Android Keystore 密钥加密存入本机不可备份目录，仅用于会话无法复用时的自动恢复。可在学校账号页关闭自动登录并删除保存的密码，解绑或退出学校账号时一并清除；重新登录教务账号后默认启用自动恢复。密码、验证码和学校会话不发送到我的川农后台，不写入日志。不会采集川农 e 官方网页中的密码；学校要求的验证码、短信或二次认证仍由本人完成。我的川农账号与学校账号分别管理。",
            ),
            V1InformationSection(
                "传输与本地保存",
                "学校认证使用 HTTPS，不忽略证书错误。认证 Cookie 由本机 Android Keystore 密钥加密保存于不可备份目录；网页会话与必要缓存留在本应用内，可单独清除。可选入学年份仅用于本机学期名称，在本人身份确认后关联保存；不会从川农学号猜测年级。本人学习数据解析仅在本机进行，不向自有后端上传学校密码、验证码、会话和学习数据。",
            ),
            V1InformationSection(
                "自有服务",
                "用户主动登录、发布失物招领、提交反馈或使用资料库时，相关账号信息和用户提交内容仅发送至配置完成的我的川农自有后端。为区分独立设备与安装实例，App 还会在本机将 Android 系统提供的 ANDROID_ID 做 App 范围内的单向派生后发送 deviceScopedId；原始 ANDROID_ID、IMEI、IMSI、手机序列号和 MAC 地址不会发送。后端再使用服务端密钥保存不可逆的 device_key_hash，并保存本次安装的 installationId、版本信息，用于独立设备统计、安装实例去重、账号安全和服务稳定性。当前内部预览仅连接独立本地后端，需通过 ADB reverse 联调；后端不可达不影响浏览主要页面。",
            ),
            V1InformationSection(
                "本地数据与权限",
                "学校绑定、学校缓存、手动课表、课程提醒、桌面小组件和记账数据保存在设备本地。联网用于学校查询、自有服务和用户主动打开的外部网页；通知、开机接收及精确闹钟仅服务课程提醒。用户可在账号与数据管理中解绑学校账号并清除本地学习数据。",
            ),
        )
        ReleaseDocumentType.AGREEMENT -> listOf(
            V1InformationSection(
                "服务性质",
                "我的川农是个人开发者独立开发的非官方校园生活辅助工具，不代表学校，不构成学校授权或合作。",
            ),
            V1InformationSection(
                "首发限制",
                "学校查询仅作为用户本人访问学校既有服务的非官方辅助入口。校园地图经用户同意后使用高德底图与公开校园地点，采用高德公开地点的原名与坐标。不提供手机实时定位或路线导航，请以校园实际指引为准。V1 不提供真实体育预约或真实支付；服务不可用时应用不会伪造查询、预约或支付成功结果。",
            ),
            V1InformationSection(
                "用户内容",
                "用户应仅发布有权提交的内容，不得发布违法、侵权或不必要的敏感信息。失物招领支持举报与后台治理。",
            ),
        )
        ReleaseDocumentType.THIRD_PARTY -> listOf(
            V1InformationSection("高德开放平台地图 SDK（含搜索、定位合包）", AMAP_DISCLOSURE),
            V1InformationSection(
                "Android 本地组件",
                androidComponentsDescription,
            ),
            V1InformationSection(
                "校园服务依赖",
                "产品页面与通用能力继承自已验收源码；川农学校网络适配独立实现，不包含长理专用网络库，不代表学校官方授权。",
            ),
            V1InformationSection(
                "明确不包含",
                "V1 不包含 Lite3DMap、支付 SDK或真实体育履约实现。高德合包中的定位组件未初始化，不申请定位、Wi-Fi 或电话权限。",
            ),
            V1InformationSection(
                "外部公开网页",
                "用户主动打开四六级、普通话或资料链接时，由对应网页运营方处理其网络请求。",
            ),
        )
        ReleaseDocumentType.PERSONAL_INFORMATION -> listOf(
            V1InformationSection("高德地图服务", AMAP_DISCLOSURE),
            V1InformationSection(
                "发送至自有后端",
                "我的川农账号信息、App 本次安装的 installationId、App 范围内单向派生的 deviceScopedId、客户端版本信息，以及用户主动提交的失物招领内容、可选图片、反馈和资料库交互信息。服务端仅保存由服务端密钥再次计算的 device_key_hash，不保存原始 ANDROID_ID；IMEI、IMSI、手机序列号和 MAC 地址不会发送。上述设备相关标识用于独立设备统计、安装实例去重、账号安全和服务稳定性。",
            ),
            V1InformationSection(
                "不会发送至自有后端",
                "学号、学校密码、验证码、学校 Cookie/session、学校 token，以及课表、成绩、考试、MOOC、校历、电量和校园卡查询结果。它们仅在用户主动使用相应功能时发往对应学校服务或在本机处理。",
            ),
            V1InformationSection(
                "仅在本地",
                "学校网页会话、经明确授权保存的教务凭据、本人学习缓存、手动课表、提醒、小组件及记账数据仅保存在本机。学校会话、教务自动登录凭据和自有账号 token 分别使用 Android Keystore 密钥加密。学校验证本人身份后才保存绑定；联网失败不删除已成功保存的学习数据。",
            ),
        )
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
    }

private const val AMAP_DISCLOSURE = "提供方为北京高德图强科技有限公司。仅在用户阅读并同意地图说明后启动 SDK，用于校园底图与公开地点展示。SDK 在本机采集并向高德处理地图视野经纬度、校园地点查询词、IP 与网络类型、设备品牌型号、系统与应用版本、屏幕参数、OAID（可用时）及传感器等必要信息，具体取决于系统和已授权权限。本功能不启用设备定位，不申请定位、Wi-Fi 或电话权限，不向高德发送学校账号、密码、会话和学习数据。输入框文字在本机搜索，不发送输入内容；加载三校区地点时使用固定的学校名称与建筑类别查询词。公开地点结果在本机缓存七天。完整处理范围与权利说明见高德开放平台隐私政策：https://lbs.amap.com/pages/privacy/。"
