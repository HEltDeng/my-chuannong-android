package com.mychuannong.app.core.web

import java.net.URI

/** Recognize official navigation only; never manufacture OAuth parameters or tickets. */
internal object ElectricAuthorizationPolicy {
    fun provider(url: String): Boolean = ServiceWebPolicy.httpUrl(url) && runCatching {
        val u = URI(url)
        u.host == "bd.bdfairy.cn" && u.port in listOf(-1, 80, 443)
    }.getOrDefault(false)

    fun wechatAuthorization(url: String): Boolean = ServiceWebPolicy.httpUrl(url) && runCatching {
        val u = URI(url)
        u.scheme == "https" && u.host == "open.weixin.qq.com" && u.port in listOf(-1, 443) &&
            u.path in setOf("/connect/oauth2/authorize", "/connect/qrconnect", "/connect/oauth2/authorize/", "/connect/qrconnect/")
    }.getOrDefault(false)

    fun inspect(url: String) = provider(url) || wechatAuthorization(url)

    // Return only a status, not the DOM, account information or authorization URL.
    const val statusProbe = """(function(){
        var text=document.body?document.body.innerText:'';
        if(/请在微信(客户端|中|内).*打开|请使用微信.*打开|只能在微信.*打开|不支持.*(WebView|内置浏览器)/i.test(text))return 'external';
        if(/授权失败|授权已取消|拒绝授权|登录已过期|登录失效|会话过期/.test(text))return 'expired';
        var title=document.querySelector('#errorApp .error-title');
        var message=document.querySelector('#errorApp .error-message');
        if(title && message && title.innerText.trim()==='操作失败' && /网络异常/.test(message.innerText))return 'provider_error';
        return 'none';
    })()"""
}
