package com.mychuannong.app.core.web

import java.net.URI
import com.google.gson.Gson

/** Public page layout only. No form values or results are read by native code. */
object ServiceWebPolicy {
    const val connectionErrorProbe = "(function(){return !!document.body && /net::ERR_[A-Z_]+/.test(document.body.innerText);})()"
    const val emptyPageProbe = "(function(){return document.readyState==='complete' && !!document.body && !document.body.innerText.trim() && !document.querySelector('iframe,frame,img,canvas,svg,video,input,button');})()"
    fun httpUrl(url: String): Boolean = runCatching {
        require(url.none { it.code < 32 || it.code == 127 } && !url.contains('\\'))
        val u = URI(url.substringBefore('#'))
        u.scheme in setOf("https", "http") && !u.host.isNullOrBlank() && u.userInfo == null
    }.getOrDefault(false)
    fun isAuthentication(url: String) = Regex("(?i)(/login|/cas/|/sso/|/oauth|/callback)").containsMatchIn(runCatching { URI(url).path }.getOrDefault("/login"))
    fun previousBusinessIndex(urls: List<String>, current: Int): Int? = (current - 1 downTo 0).firstOrNull {
        httpUrl(urls[it]) && !isAuthentication(urls[it]) && urls[it] != urls[current]
    }
    fun externalScheme(url: String): Boolean = runCatching {
        val scheme = URI(url).scheme?.lowercase()
        !scheme.isNullOrBlank() && scheme !in setOf("http", "https", "file", "data", "javascript", "content", "intent", "about", "blob")
    }.getOrDefault(false)
    fun paymentScheme(url: String): Boolean = runCatching { URI(url).scheme?.lowercase() in setOf("alipays", "alipay", "weixin") }.getOrDefault(false)
    // Preserve an existing mobile viewport and all original styles and interactions.
    const val mobileViewport = "(function(){if(document.head&&!document.querySelector('meta[name=viewport]')){var m=document.createElement('meta');m.name='viewport';m.content='width=device-width,initial-scale=1';document.head.appendChild(m);}})();"
    fun adaptation(url: String): String? {
        val u = runCatching { URI(url) }.getOrNull() ?: return null
        if(u.scheme != "https") return null
        val css = when {
            u.host == "zwfw.moe.gov.cn" && u.path.startsWith("/mandarin/") -> """
                html,body{min-width:0!important;width:100%!important;margin:0;box-sizing:border-box;overflow-wrap:break-word}
                .main,.search,.search-details,.search-content,.result,.note,.breadcrumb{width:auto!important;max-width:100%!important;min-width:0!important;box-sizing:border-box;margin-left:0!important;margin-right:0!important}
                .main{padding:12px!important}.search-details{padding:12px!important}.search-content{float:none!important}
                .search-content .row{display:flex!important;flex-wrap:wrap!important;height:auto!important;margin:14px 0!important;width:100%!important}
                .search-content .left{width:100%!important;text-align:left!important;margin:0 0 6px!important}
                .search-content .right{width:100%!important;display:flex!important;flex-wrap:wrap;gap:8px;align-items:center;box-sizing:border-box}
                .search-content .input-dom{width:100%!important;min-width:0!important;max-width:100%;font-size:16px!important;box-sizing:border-box;margin:0!important}
                .search-content input:focus{outline:2px solid #237b62!important;outline-offset:1px}
                .search-content .code-value{width:45%!important;flex:1 1 110px}
                #canvas{width:80px!important;height:auto!important;object-fit:contain;flex:none}
                .forget-examId{position:static!important;width:auto!important;margin:8px 0!important;white-space:normal!important}
                .btn-list .right{display:flex!important;flex-wrap:wrap}.btn-list .btn{width:auto!important;min-width:85px;margin:0!important;flex:1;box-sizing:border-box}
                .desc-list,.desc-lists{width:auto!important;max-width:100%;padding-left:12px!important;box-sizing:border-box}
                .search>.title,.result-title{font-size:20px!important;line-height:1.5!important;height:auto!important;padding:10px!important}
                .result table{display:block;max-width:100%;overflow-x:auto}.result-main3{width:100%!important}
                header,footer{max-width:100%;overflow-x:auto} iframe{max-width:100%}
            """.trimIndent()
            else -> return null
        }
        return """(function(){if(document.getElementById('mychuannong-mobile-layout'))return;var s=document.createElement('style');s.id='mychuannong-mobile-layout';s.textContent=${Gson().toJson(css)};document.head.appendChild(s);if(!document.querySelector('meta[name=viewport]')){var m=document.createElement('meta');m.name='viewport';m.content='width=device-width,initial-scale=1';document.head.appendChild(m);}document.addEventListener('focusin',function(e){if(/^(INPUT|TEXTAREA|SELECT)$/.test(e.target.tagName))setTimeout(function(){e.target.scrollIntoView({block:'center'});},350);});})();"""
    }
}
