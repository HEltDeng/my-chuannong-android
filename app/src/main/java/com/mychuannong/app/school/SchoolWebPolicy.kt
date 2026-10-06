package com.mychuannong.app.school
import java.net.URI
object SchoolWebPolicy {
    fun isAuthentication(url:String):Boolean = runCatching {
        val u=URI(url.substringBefore('#'));u.host=="auth.sicau.edu.cn" || Regex("(?i)(/login|/cas/|/sso/|/oauth|/callback)").containsMatchIn(u.path.orEmpty()+url.substringAfter('#', "").substringBefore('?'))
    }.getOrDefault(true)
    fun previousBusinessIndex(urls:List<String>,current:Int):Int? = (current-1 downTo 0).firstOrNull {
        com.mychuannong.app.SafeUrl.school(urls[it]) && !isAuthentication(urls[it]) && urls[it]!=urls[current]
    }
    /** Narrow public reading/form adjustments; never hides a business control. */
    fun adaptation(url:String):String? {
        val u=runCatching{URI(url.substringBefore('#'))}.getOrNull()?:return null
        if(!com.mychuannong.app.SafeUrl.school(url) || isAuthentication(url)) return null
        val css=when(u.host) {
            "www.sicau.edu.cn" -> "#vsb_content{font-size:17px;line-height:1.75;overflow-wrap:anywhere} #vsb_content img{max-width:100%!important;height:auto!important} #vsb_content table{display:block;max-width:100%;overflow-x:auto}"
            "jiaowu.sicau.edu.cn" -> "body{overflow-wrap:break-word} img{max-width:100%;height:auto} table{max-width:100%} input,select,textarea{max-width:100%;box-sizing:border-box}"
            "auth.sicau.edu.cn" -> "input,select,textarea{font-size:16px!important;max-width:100%;box-sizing:border-box}"
            "ywtb.sicau.edu.cn" -> """
                input,select,textarea{max-width:100%;box-sizing:border-box;font-size:16px} img{max-width:100%;height:auto}
                .container-center{width:100%!important;max-width:100%;box-sizing:border-box;padding:0 12px}
                .base[data-v-eca783be]{width:100%!important;display:block!important;margin-top:130px!important}
                .base[data-v-eca783be]>.left,.base[data-v-eca783be]>.right{width:100%!important;margin:0!important;height:auto!important}
                .base[data-v-eca783be]>.right>.down{display:block!important;width:100%!important;height:auto!important}
                .base[data-v-eca783be]>.right>.up,.main-swiper{width:100%!important;max-width:100%;margin-top:16px}
                .gird-navi[data-v-7fa9377e],.hot-intro[data-v-4b45c681]{width:100%!important;height:auto!important;box-sizing:border-box;margin:12px 0!important;padding:16px!important}
                .grid-item[data-v-7fa9377e]>.grid-item-box{width:33.333%!important;height:110px!important;padding-top:12px!important}
                .grid-item-box[data-v-7fa9377e] .text{height:auto!important;margin-top:8px!important;font-size:14px!important}
                .menu[data-v-234be42d],.menu .main[data-v-234be42d]{width:100%!important}
                .menu .main[data-v-234be42d]>li,.menu .first-main[data-v-234be42d],.menu .inner-first[data-v-234be42d],.menu .main>li .first[data-v-234be42d]{width:100%!important;box-sizing:border-box}
                .menu .second[data-v-234be42d]{position:static!important;width:100%!important;max-height:50vh;overflow:auto;box-sizing:border-box}
                .menu .second>.left,.menu .second>.right{width:100%!important;min-width:0!important}
                .entrance[data-v-391223ec],.goods[data-v-4fe94ca6],.data-view{width:100%!important;min-width:0!important;max-width:100%;height:auto!important;overflow-x:auto;margin:16px 0!important}
                .container-list[data-v-6985a498]{margin-top:140px!important}
                .container-list-menu[data-v-6985a498]{display:flex!important;flex-direction:column;width:100%;margin:0!important}
                .container-list-menu[data-v-6985a498]>.uni-col{width:100%!important;float:none!important;padding:12px 0!important;box-sizing:border-box}
                .container-list-menu[data-v-6985a498]>.uni-col-5{max-height:280px;overflow:auto}
                .subcatename[data-v-6985a498]>.uni-col{width:100%!important;float:none!important;padding:6px 0!important}
                .subcatetitle[data-v-6985a498]{display:inline-block!important;width:auto!important;margin:4px!important}
                .subcatetitle[data-v-6985a498] uni-button{padding:0 12px!important}
                .container-list[data-v-6985a498] .uni-table-scroll{max-width:100%;overflow-x:auto}
                .life-area{width:100%!important;max-width:100%}.container-center table{display:block;max-width:100%;overflow-x:auto}
            """.trimIndent()
            "op.sicau.edu.cn" -> (if(u.path.equals("/loginAll.aspx",true)) "#wraper,#user{width:100%!important;max-width:100%;box-sizing:border-box} .login_news{width:100%!important;float:none!important;overflow-x:auto} #tr_l1{width:100%!important} " else "") + "input,select,textarea{max-width:100%;box-sizing:border-box;font-size:16px} body{overflow-wrap:break-word}"
            else -> return null
        }
        return """(function(){if(document.getElementById('sicau-app-reading'))return;var s=document.createElement('style');s.id='sicau-app-reading';s.textContent=${com.google.gson.Gson().toJson(css)};document.head.appendChild(s);if(!document.querySelector('meta[name=viewport]')){var m=document.createElement('meta');m.name='viewport';m.content='width=device-width,initial-scale=1';document.head.appendChild(m);}document.addEventListener('focusin',function(e){if(/^(INPUT|TEXTAREA|SELECT)$/.test(e.target.tagName))setTimeout(function(){e.target.scrollIntoView({block:'center',behavior:'smooth'});},350);});})();"""
    }
}
