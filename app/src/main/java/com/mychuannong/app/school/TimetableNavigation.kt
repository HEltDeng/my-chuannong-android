package com.mychuannong.app.school

/** Only a link supplied by the student's current authenticated school page may be followed. */
object TimetableNavigation {
    fun allowed(url:String):Boolean = runCatching {
        val u=java.net.URI(url)
        u.scheme=="https" && u.host=="jiaowu.sicau.edu.cn" && u.userInfo==null && u.port in listOf(-1,443) &&
            u.path=="/xuesheng/gongxuan/gongxuan/kbbanji.asp"
    }.getOrDefault(false)
    val script="""
        (() => {
          const links=new Set(); let seen=0;
          function scan(w,depth) {
            if(depth>6 || ++seen>60) return;
            try {
              if(w.location.origin!=='https://jiaowu.sicau.edu.cn') return;
              for(const a of w.document.querySelectorAll('a[href]')) {
                if(!a.getClientRects().length) continue;
                const text=a.textContent.replace(/\s+/g,'');
                if(!text.includes('课表')) continue;
                const url=new URL(a.getAttribute('href'),w.document.baseURI);
                if(url.origin==='https://jiaowu.sicau.edu.cn' && url.pathname==='/xuesheng/gongxuan/gongxuan/kbbanji.asp' && !url.username && !url.password) links.add(url.href);
              }
              for(let i=0;i<w.frames.length;i++) scan(w.frames[i],depth+1);
            } catch(_) {}
          }
          scan(window,0);
          return links.size===1 ? Array.from(links)[0] : '';
        })()
    """.trimIndent()
}
