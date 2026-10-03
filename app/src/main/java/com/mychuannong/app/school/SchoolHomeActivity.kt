package com.mychuannong.app.school

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mychuannong.app.SchoolWebActivity
import com.mychuannong.app.base.ComposeActivity
import com.mychuannong.app.core.theme.AppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.jsoup.Jsoup

/** Anonymous read-only information. The original site remains available from the menu. */
class SchoolHomeActivity:ComposeActivity() {
    data class News(val title:String,val url:String)
    override fun onCreate(state:Bundle?) {
        super.onCreate(state)
        setComposeContent {
            var news by remember{mutableStateOf<List<News>>(emptyList())}
            var article by remember{mutableStateOf<News?>(null)}
            var paragraphs by remember{mutableStateOf<List<String>>(emptyList())}
            var error by remember{mutableStateOf<String?>(null)}
            var loading by remember{mutableStateOf(false)}
            val scope=rememberCoroutineScope()
            fun web(url:String){startActivity(Intent(this,SchoolWebActivity::class.java).putExtra("url",url).putExtra("title",if(article==null)"学校官网" else "资讯原文"))}
            fun load(){scope.launch {
                loading=true;error=null
                val current=article
                runCatching {withContext(Dispatchers.IO) {
                    val url=current?.url?:"https://www.sicau.edu.cn/"
                    val html=SicauAcademicData.client.newCall(Request.Builder().url(url).build()).execute().use{check(it.isSuccessful);it.body!!.string()}
                    val doc=Jsoup.parse(html,url)
                    if(current==null) {
                        val rows=doc.select("a[href]").mapNotNull { a ->
                            val link=a.absUrl("href").replace("http://news.sicau.edu.cn/","https://news.sicau.edu.cn/")
                            val text=a.selectFirst("h2,h3,h4,h5,b")?.text()?:a.selectFirst("img[alt]")?.attr("alt")?.takeIf{it.isNotBlank()}?:a.text()
                            if(link.startsWith("https://news.sicau.edu.cn/info/") && text.length>10) News(text.take(100),link) else null
                        }.distinctBy{it.url}.take(30)
                        check(rows.isNotEmpty());rows to emptyList<String>()
                    } else {
                        val content=doc.selectFirst(".v_news_content")?:error("正文未找到")
                        val rows=content.select("p").map{it.text()}.filter{it.isNotBlank()}
                        check(rows.isNotEmpty());emptyList<News>() to rows
                    }
                }}.onSuccess{(n,p)->if(current==null)news=n else paragraphs=p}.onFailure{error="暂时无法读取资讯，可重试或查看官网"}
                loading=false
            }}
            fun back(){if(article!=null){article=null;paragraphs=emptyList()}else finish()}
            LaunchedEffect(Unit){load()}
            BackHandler{back()}
            Scaffold(containerColor=AppTheme.colors.bgPrimaryColor,topBar={SchoolPageTopBar("学校官网",{back()},{load()},listOf("在浏览器中打开" to {openOfficialBrowser(this,article?.url?:"https://www.sicau.edu.cn/")},"关闭本页" to {finish()},"查看完整官网" to {web("https://www.sicau.edu.cn/")},"在官网查看原文" to {web(article?.url?:"https://www.sicau.edu.cn/")}))}) { padding ->
                Column(Modifier.fillMaxSize().padding(padding)) {
                    if(loading)LinearProgressIndicator(Modifier.fillMaxWidth())
                    error?.let{Text(it,Modifier.padding(16.dp))}
                    LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                        item{Text(article?.title?:"川农资讯",fontSize=22.sp,color=AppTheme.colors.titleTopColor)}
                        if(article==null) items(news,key={it.url}){n->
                            Column(Modifier.fillMaxWidth().clickable{article=n;paragraphs=emptyList();load()}.padding(vertical=8.dp)) {
                                Text(n.title,fontSize=16.sp);Spacer(Modifier.height(10.dp));HorizontalDivider(color=AppTheme.colors.dividerColor)
                            }
                        } else items(paragraphs){Text(it,fontSize=17.sp,lineHeight=28.sp)}
                        item{Text("来源：四川农业大学官网",fontSize=12.sp,color=AppTheme.colors.greyTextColor)}
                    }
                }
            }
        }
    }
}
