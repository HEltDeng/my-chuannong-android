package com.mychuannong.app.school

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Display policy only. A visible login page is never evidence of a valid VPN session. */
internal object WebVpnPagePresentation {
    fun isAuthentication(url: String): Boolean {
        val u = url.toHttpUrlOrNull() ?: return false
        if (u.scheme != "https" || u.port != 443 || u.username.isNotEmpty() || u.password.isNotEmpty()) return false
        if (u.host == "auth.sicau.edu.cn") return true
        if (u.host != SicauJiaowuRoute.VPN_HOST) return false
        val route = SicauJiaowuRoute.fromResourcePage(url)
        val path = route?.let { u.encodedPath.removePrefix(it.prefix) } ?: u.encodedPath
        return (route == null && path == "/login") || path == "/wui" || path.startsWith("/wui/")
    }

    fun mayContainRootLogin(url: String): Boolean {
        val u = url.toHttpUrlOrNull() ?: return false
        return u.scheme == "https" && u.host == SicauJiaowuRoute.VPN_HOST && u.port == 443 &&
            u.username.isEmpty() && u.password.isEmpty() && u.encodedPath == "/"
    }

    // Some VPN versions render their login at /. Only expose real authentication controls.
    // An in-place replacement with resource cards is hidden before the browser paints it.
    // No values, credentials, cookies or form contents are read or submitted.
    val rootLoginGuard = """
        (function(){
            function visible(e){return e.getBoundingClientRect().width>0 && e.getBoundingClientRect().height>0;}
            function login(){return Array.from(document.querySelectorAll(
                'input[type=password],input[autocomplete=one-time-code],input[name=captcha],input[name=otp]'
            )).some(visible);}
            function guard(){
                var show=login(), root=document.documentElement;
                if(root){
                    // Opacity preserves innerText for the existing official resource-link parser.
                    var v=show?'1':'0', p=show?'auto':'none';
                    if(root.style.opacity!==v)root.style.setProperty('opacity',v,'important');
                    if(root.style.pointerEvents!==p)root.style.setProperty('pointer-events',p,'important');
                    if(root.hasAttribute('inert')===show)root.toggleAttribute('inert',!show);
                    if(root.getAttribute('aria-hidden')!==String(!show))root.setAttribute('aria-hidden',String(!show));
                }
                return show;
            }
            if(!window.__campusVpnLoginGuard && document.documentElement){
                window.__campusVpnLoginGuard=new MutationObserver(guard);
                window.__campusVpnLoginGuard.observe(document.documentElement,{childList:true,subtree:true,attributes:true});
            }
            return guard();
        })();
    """.trimIndent()
}
