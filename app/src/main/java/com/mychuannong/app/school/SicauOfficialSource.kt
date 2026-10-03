package com.mychuannong.app.school

import okhttp3.Request
import org.jsoup.Jsoup
import java.security.MessageDigest
import java.time.ZonedDateTime

/** Direct anonymous official-source reader. URLs are rediscovered on every expired/manual check. */
object SicauOfficialSource {
    data class Discovery(val academicYear:String,val resources:List<Pair<PublicSource,ByteArray>>,val fingerprint:String)
    fun sha256(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)}
    fun discover(html:String,download:(String)->ByteArray):Discovery {
        val doc=Jsoup.parse(html,SicauAcademicData.SOURCE)
        val years=Regex("(20\\d{2})[-—–](20\\d{2})学年").findAll(doc.text()).map{"${it.groupValues[1]}-${it.groupValues[2]}"}.distinct().toList()
        require(years.size==1){"官方学年信息需核对"}
        val urls=doc.select("#vsb_content img[src]").map{it.absUrl("src")}.distinct()
        require(urls.size in 1..4 && urls.all{it.startsWith("https://www.sicau.edu.cn/__local/")}){"官方校历版式有变化，核对中"}
        val resources=urls.map { url -> val bytes=download(url);PublicSource(url,sha256(bytes)) to bytes }
        return Discovery(years.single(),resources,sha256(resources.map{it.first.sha256}.sorted().joinToString("").toByteArray()))
    }
    private fun download(url:String):ByteArray {
        require(url.startsWith("https://www.sicau.edu.cn/"))
        return SicauAcademicData.client.newCall(Request.Builder().url(url).build()).execute().use { r ->
            check(r.isSuccessful && r.request.url.host=="www.sicau.edu.cn" && r.request.url.isHttps){"官方来源暂不可用"}
            val body=r.body?:error("官方来源内容为空")
            body.byteStream().use { input ->
                val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
                while(true){val n=input.read(buffer);if(n<0)break;require(out.size()+n<=8*1024*1024);out.write(buffer,0,n)}
                out.toByteArray()
            }
        }
    }
    fun check(active:PublicAcademicData):PublicAcademicData {
        val found=discover(download(SicauAcademicData.SOURCE).toString(Charsets.UTF_8),::download)
        if(found.fingerprint!=active.contentFingerprint || found.academicYear!=active.academicYear) {
            // A downloaded image is not structured data. Unknown artwork cannot replace verified rules.
            return runCatching { SicauImageParser.parse(found,active).also { SicauAcademicData.parse(com.google.gson.Gson().toJson(it)) } }.getOrElse { throw IllegalStateException("官方原件有变化，解析待核对；保留已验证校历",it) }
        }
        // Content unchanged but URLs may have moved. Keep the calendar image first for the viewer.
        val ordered=active.sourceResources.map{old->found.resources.first{it.first.sha256==old.sha256}.first}
        return active.copy(terms=active.terms.map{t->t.copy(sourceResource=ordered.firstOrNull{it.sha256==t.sourceFingerprint}?.url?:t.sourceResource)},sourceResources=ordered,checkedAt=ZonedDateTime.now(SicauAcademicData.zone).toString())
    }
}
