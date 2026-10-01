package com.mychuannong.app

import android.content.*
import android.net.Uri
import android.os.Bundle
import android.webkit.*
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mychuannong.app.core.theme.*
import com.mychuannong.app.feature.common.compose_ui.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class MainActivity: ComponentActivity() {
 private val prefs by lazy {getSharedPreferences("my_chuannong_local",MODE_PRIVATE)}
 private lateinit var builtin:JSONObject
 private var config by mutableStateOf(JSONObject())
 private var message by mutableStateOf("")
 private var selected by mutableStateOf<JSONObject?>(null)
 private var page by mutableStateOf("首页")
 private var accepted by mutableStateOf(false)
 private var showPolicy by mutableStateOf(false)
 override fun onCreate(savedInstanceState:Bundle?) {
  super.onCreate(savedInstanceState)
  builtin=JSONObject(assets.open("school.json").bufferedReader().readText());config=builtin
  accepted=prefs.getBoolean("consent_v1",false)
  setContent { AppSkinTheme { Surface(Modifier.fillMaxSize(),color=Color(0xFFF5F7F8)) { if(!accepted) Welcome() else Root() } } }
 }
 private fun copy(text:String) {(getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("名称",text));Toast.makeText(this,"已复制",Toast.LENGTH_SHORT).show()}
 private fun external(url:String) { if(!SafeUrl.https(url)){message="链接格式不受支持";return};try{startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url)))}catch(_:ActivityNotFoundException){message="没有可用的浏览器，请安装浏览器后重试"} }
 private fun services():List<JSONObject>{val arr=config.getJSONArray("services");return (0 until arr.length()).map{arr.getJSONObject(it)}.filter{it.optBoolean("visible",true)}}
 private fun icon(id:String)=when(id){"schedule","calendar"->Icons.Outlined.CalendarMonth;"grades"->Icons.Outlined.School;"exams"->Icons.Outlined.EditNote;"electric"->Icons.Outlined.Bolt;"card","payment"->Icons.Outlined.CreditCard;"water"->Icons.Outlined.WaterDrop;"map"->Icons.Outlined.Map;"dorm"->Icons.Outlined.Home;"library","resources"->Icons.Outlined.MenuBook;"auth"->Icons.Outlined.Lock;else->Icons.Outlined.Language}
 @Composable private fun Brand(size:Int=72){Surface(shape=RoundedCornerShape((size/4).dp),color=Color.White,modifier=Modifier.size(size.dp).dropShadow(RoundedCornerShape((size/4).dp),Shadow(radius=14.dp,spread=1.dp,color=Color(0x287B9FCB),offset=DpOffset(0.dp,6.dp)))){Image(painterResource(R.drawable.product_mark),"我的川农产品标识",Modifier.padding(8.dp))}}
 @Composable private fun Welcome(){if(showPolicy) AlertDialog(onDismissRequest={showPolicy=false},title={Text("隐私说明与用户协议")},text={Text("本产品为独立校园便利工具，非学校官方 App。学校及服务商处理其页面内主动输入的信息；本应用网页容器保存必要 Cookie、缓存和站点存储，可在设置清除。应用不注入登录脚本，不读取、导出或上传学校密码、验证码、Cookie 和受保护页面，不接入广告及分析 SDK。游客无需注册，本地偏好保存在本应用。学校和第三方服务适用各自规则，预览版不提供收费或正式发布服务。")},confirmButton={TextButton(onClick={showPolicy=false}){Text("返回")}});Column(Modifier.fillMaxSize().safeDrawingPadding().padding(28.dp)){Spacer(Modifier.height(68.dp));Brand(104);Spacer(Modifier.height(26.dp));Text("欢迎使用我的川农",fontSize=27.sp,fontWeight=FontWeight.Bold);Spacer(Modifier.height(12.dp));Text("四川农业大学校园服务入口\nMyChuannong · 内部预览",color=Color(0xFF677484));Spacer(Modifier.height(24.dp));Text("这是独立校园便利工具，非学校官方 App。学校网站由您自行登录，本产品不读取或上传学校密码、验证码和网站会话。网页会话按需要保存在本应用本地，可在设置中清除。",lineHeight=25.sp);Spacer(Modifier.weight(1f));TextButton(onClick={showPolicy=true}){Text("阅读隐私说明与用户协议")};Button(onClick={prefs.edit().putBoolean("consent_v1",true).apply();accepted=true;page="首页"},modifier=Modifier.fillMaxWidth()){Text("同意并进入")};TextButton(onClick={finish()},modifier=Modifier.fillMaxWidth()){Text("暂不使用")}}}
 @Composable private fun Root(){
 BackHandler(enabled=selected!=null || page !in listOf("首页","服务","我的")){selected=null;page="我的"}
 Column(Modifier.fillMaxSize().safeDrawingPadding()){
  Row(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=14.dp),verticalAlignment=Alignment.CenterVertically){Text(if(selected!=null)selected!!.getString("name") else page,fontSize=24.sp,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f));if(selected!=null || page !in listOf("首页","服务","我的"))TextButton(onClick={selected=null;page="我的"}){Text("返回")}}
  Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal=18.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
   if(selected!=null) Detail(selected!!) else when(page){
    "首页"->{Row(verticalAlignment=Alignment.CenterVertically){Brand();Column(Modifier.padding(start=16.dp)){Text("我的川农",fontSize=25.sp,fontWeight=FontWeight.Bold);Text("让校园查询更方便",color=Color(0xFF677484))}};Info("四川农业大学","雅安校区 · 成都校区 · 都江堰校区\n独立校园便利工具 · 内部预览");Info("学校服务，由您自行登录","课表、成绩与考试请在学校网页查询。若教务暂时打不开，可稍后重试或切换到您通常使用的网络。");Section("常用服务",services().filter{it.getString("id") in listOf("schedule","grades","exams","electric","card","hall","school","calendar")});Action("查看全部服务"){page="服务"}}
    "服务"->{Text("网页查询、公众号与外部应用分开呈现",color=Color(0xFF677484));for(group in listOf("学习服务","校园生活","校园信息"))Section(group,services().filter{it.getString("group")==group})}
    "我的"->{Row(verticalAlignment=Alignment.CenterVertically){Brand();Column(Modifier.padding(start=16.dp)){Text("游客",fontSize=23.sp,fontWeight=FontWeight.Bold);Text("无需注册即可浏览学校服务",color=Color(0xFF677484))}};Action("学校服务登录"){selected=services().first{it.getString("id")=="auth"}};Text("学校网页登录不等于本产品账号绑定",color=Color(0xFF677484));Action("设置"){page="设置"};Action("关于我的川农"){page="关于"};Action("隐私说明与用户协议"){page="隐私与协议"};Action("我的川农官网"){page="官网筹备中"}}
    "设置"->{Info("本地学校网页登录状态","清除仅影响本应用内的网页 Cookie、缓存与站点存储。不会清除外部浏览器或其他 App 的会话。");Action("清除本应用学校网页登录状态"){CookieManager.getInstance().removeAllCookies{CookieManager.getInstance().flush();WebStorage.getInstance().deleteAllData();WebView(this@MainActivity).apply{clearCache(true);clearHistory();destroy()};message="本应用网页会话已清除"}};Info("本地后端联调","默认未连接后端。开启后仅访问 127.0.0.1:8791；真机需先配置 ADB reverse。关闭电脑后端仍可使用内置学校入口。");var enabled by remember{mutableStateOf(prefs.getBoolean("local_backend",false))};Row(verticalAlignment=Alignment.CenterVertically){Text("连接本产品本地后端",Modifier.weight(1f));Switch(enabled,{enabled=it;prefs.edit().putBoolean("local_backend",it).apply();if(!it){config=builtin;message="已恢复内置配置"}})};Action("刷新独立配置"){refreshConfig()};Action("恢复内置服务清单"){config=builtin;message="已恢复内置配置"};Info("版本更新","0.1.0-preview.1 / 1\n内部预览，无正式更新记录，不强制更新。")}
    "关于"->{Brand(104);Info("我的川农 MyChuannong","版本 0.1.0-preview.1（1）\n包名 com.mychuannong.app\nSichuan Agricultural University\n文档英文简称 SAU\n本产品非学校官方 App，不代表学校或服务商。临时青绿色为产品配色，并非学校官方标准色。");Action("四川农业大学官网"){external("https://www.sicau.edu.cn/")};Action("我的川农官网"){page="官网筹备中"};Info("使用与反馈","如发现入口失效，请记录服务名称、网络环境和操作步骤，交给项目负责人。请勿提供密码、验证码或未脱敏学生资料。");Action("分享产品说明"){val send=Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,"我的川农 MyChuannong：四川农业大学校园便利查询工具，内部预览，非学校官方 App，暂未公开下载。");startActivity(Intent.createChooser(send,"分享说明"))}}
    "官网筹备中"->Info("我的川农官网筹备中","本产品尚未配置官网和公开下载地址。四川农业大学官网为独立的学校服务入口。")
    "隐私与协议"->{Info("预览版隐私说明","2026-10-01\n学校网页由学校及服务商处理您主动填写的信息。应用内 WebView 按网站正常运行需要保存 Cookie、缓存和站点存储；您可在设置清除。外部浏览器、微信、完美校园由各自隐私政策约束。\n本产品不注入学校登录脚本，不读取、导出或上传学校账号密码、验证码、Cookie 和受保护学生页面，不接入广告、分析或崩溃上报 SDK。\n游客模式不建立本产品用户账号；本地偏好只保存在本应用沙箱。选择本地联调时，仅请求本产品配置和公开内容。独立后台不接收学校敏感数据。");Info("用户协议","本产品为非官方校园便利工具，当前是内部预览。学校服务是否可用受学校网站、网络与第三方条件影响。请按学校正常流程使用，不通过本产品绕过认证。平台自建内容不代表学校。预览版不提供正式下载或付费服务。");if(!prefs.getBoolean("consent_v1",false))Action("返回欢迎页"){accepted=false}}
   }
   if(message.isNotEmpty())Info("提示",message)
   Spacer(Modifier.height(12.dp))
  }
  if(prefs.getBoolean("consent_v1",false)) NavigationBar(containerColor=Color.White){listOf("首页","服务","我的").forEachIndexed { i,t->NavigationBarItem(selected=page==t&&selected==null,onClick={page=t;selected=null;message=""},icon={Icon(when(i){0->Icons.Outlined.Home;1->Icons.Outlined.GridView;else->Icons.Outlined.Person},t)},label={Text(t)})}}
 }
 }
 @Composable private fun Info(title:String,text:String){Surface(shape=RoundedCornerShape(20.dp),color=Color.White,modifier=Modifier.fillMaxWidth()){Column(Modifier.padding(18.dp)){Text(title,fontWeight=FontWeight.SemiBold,fontSize=17.sp);Spacer(Modifier.height(8.dp));Text(text,color=Color(0xFF5E6B79),lineHeight=24.sp)}}}
 @Composable private fun Action(title:String,click:()->Unit){Surface(onClick=click,shape=RoundedCornerShape(16.dp),color=Color.White,modifier=Modifier.fillMaxWidth()){Row(Modifier.padding(18.dp),verticalAlignment=Alignment.CenterVertically){Text(title,Modifier.weight(1f));Icon(Icons.Outlined.ChevronRight,null)}}}
 @Composable private fun Section(title:String,items:List<JSONObject>){FunctionSection(title,"点击查看入口和使用说明",items.map{v->FunctionItemData(v.getString("id"),v.getString("name"),icon(v.getString("id")),Color(0xFF257C71),when(v.getString("mode")){"WEBVIEW"->"学校网页";"EXTERNAL_BROWSER"->"浏览器";"WECHAT_GUIDE"->"微信引导";"EXTERNAL_APP_GUIDE"->"应用引导";"LOCAL"->"自建服务";else->"待补充"},{selected=v;message=""})})}
 @Composable private fun Detail(v:JSONObject){val mode=v.getString("mode");Info(v.getString("name"),v.getString("note"));if(!v.isNull("url")){Text(v.getString("url"),color=Color(0xFF677484));Action(if(mode=="WEBVIEW")"打开学校网页" else "使用外部浏览器打开"){val url=v.getString("url");if(mode=="WEBVIEW"&&SafeUrl.school(url))startActivity(Intent(this@MainActivity,SchoolWebActivity::class.java).putExtra("url",url).putExtra("title",v.getString("name"))) else external(url)};if(mode=="WEBVIEW")Action("外部浏览器打开"){external(v.getString("url"))}};if(mode=="WECHAT_GUIDE"||mode=="EXTERNAL_APP_GUIDE")Action("复制"+when(v.getString("id")){"student"->"川农学工";"water","card"->"完美校园";else->"川农后勤"}){copy(when(v.getString("id")){"student"->"川农学工";"water","card"->"完美校园";else->"川农后勤"})};if(mode=="LOCAL")Action("刷新独立服务内容"){loadContents(v.getString("id"))};Text("请按学校或服务商页面提示操作。预览版登录兼容性仍需进一步验证。",color=Color(0xFF677484),fontSize=12.sp)}
 private fun request(path:String):JSONObject{check(prefs.getBoolean("local_backend",false)){"请先在设置中开启本地后端联调"};val c=URL("http://127.0.0.1:8791$path").openConnection() as HttpURLConnection;c.connectTimeout=3000;c.readTimeout=3000;return try{check(c.responseCode==200);val text=c.inputStream.bufferedReader().use{reader->val buffer=CharArray(150001);var count=0;while(count<buffer.size){val n=reader.read(buffer,count,buffer.size-count);if(n<0)break;count+=n};check(count<150001);String(buffer,0,count)};JSONObject(text)}finally{c.disconnect()}}
 private fun refreshConfig(){Thread{try{val candidate=request("/api/v1/public/config");check(candidate.getString("productId")=="my-chuannong"&&candidate.getString("applicationId")==packageName&&candidate.getString("schoolId")=="sicau"&&candidate.getInt("schemaVersion")==1);check(candidate.getInt("configVersion")>=config.getInt("configVersion"));val list=candidate.getJSONArray("services");val defaults=builtin.getJSONArray("services");check(list.length()==defaults.length());check((0 until list.length()).map{list.getJSONObject(it).getString("id")}.toSet().size==list.length());for(i in 0 until list.length()){val item=list.getJSONObject(i);val base=(0 until defaults.length()).map{defaults.getJSONObject(it)}.first{it.getString("id")==item.getString("id")};check(item.getString("mode")==base.getString("mode")&&item.optString("url")==base.optString("url"));check(item.getString("name").length in 1..20);check(item.getString("group")==base.getString("group"));if(item.getString("id") in listOf("auth","school","academic"))check(item.getBoolean("visible"))};runOnUiThread{config=candidate;message="独立配置 v${candidate.getInt("configVersion")} 已加载"}}catch(_:Exception){runOnUiThread{message="配置未加载，继续使用当前安全服务清单。请检查川农后端及 ADB reverse。"}}}.start()}
 private fun loadContents(id:String){Thread{try{val r=request("/api/v1/public/$id");check(r.getString("productId")=="my-chuannong");val n=r.getJSONArray("items").length();runOnUiThread{message=if(n==0)"暂无内容。独立预览数据库为空。" else "已获取 $n 条内容；请在独立管理后台查看。"}}catch(_:Exception){runOnUiThread{message="本地服务未连接。学校网页入口仍可正常使用。"}}}.start()}
}
object SafeUrl {
 private val hosts=setOf("www.sicau.edu.cn","jiaowu.sicau.edu.cn","auth.sicau.edu.cn","ywtb.sicau.edu.cn","op.sicau.edu.cn","ecology.sicau.edu.cn","rsc.sicau.edu.cn","lib.sicau.edu.cn","xcb.sicau.edu.cn")
 fun https(s:String):Boolean=try{val u=java.net.URI(s);u.scheme=="https"&&!u.host.isNullOrBlank()&&u.rawUserInfo==null&&(u.port==-1||u.port==443)}catch(_:Exception){false}
 fun school(s:String):Boolean= https(s)&&java.net.URI(s).host.lowercase() in hosts
}
