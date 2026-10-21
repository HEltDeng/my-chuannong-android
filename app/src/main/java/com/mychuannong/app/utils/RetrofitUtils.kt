package com.mychuannong.app.utils

import android.os.Handler
import android.os.Looper
import com.mychuannong.app.BuildConfig
import com.mychuannong.app.core.PlanetApplication
import com.mychuannong.app.core.network.OkHttpHelper.AuthInterceptor
import com.mychuannong.app.core.network.interceptor.NetworkLogger
import com.mychuannong.app.core.network.LegacyServiceGuardInterceptor
import com.mychuannong.app.core.network.UnavailableEndpointInterceptor
import com.mychuannong.app.feature.mooc.cookie.PersistentCookieJar
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitUtils {
    private val CommentsIp: String = BuildConfig.COMMUNITY_COMMENTS_BASE_URL
    private val FreshNewsIp: String = BuildConfig.COMMUNITY_API_BASE_URL
    private val UserIp: String = BuildConfig.COMMUNITY_USER_BASE_URL
    private const val MOOC_LOCATION = "https://unconfigured.invalid/"
    private const val SSO_AUTH_URL = "https://unconfigured.invalid/"
    private const val SSO_EHALL_URL = "https://unconfigured.invalid/"
    private val ToolsIp: String = BuildConfig.TOOLS_API_BASE_URL

    /**
     * 上游 Go 服务端（v1）BaseUrl。
     *
     * 真实值由 Gradle 通过 `BuildConfig.PLANET_API_BASE_URL` 注入，来源优先级：
     *   1. `local.properties` 的 `planet.apiBaseUrl.debug` / `planet.apiBaseUrl.release`
     *   2. 环境变量 `PLANET_API_BASE_URL_DEBUG` / `PLANET_API_BASE_URL_RELEASE`
     *   3. 代码内 `.invalid` fail-closed 默认值（见 app/build.gradle.kts）
     *
     */
    private val PlanetIp: String = BuildConfig.PLANET_API_BASE_URL

    //添加公共请求头 - 用于需要认证的 API
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .addInterceptor(LegacyServiceGuardInterceptor())
            .addInterceptor(UnavailableEndpointInterceptor())
            .addInterceptor(NetworkLogger.getLoggingInterceptor())
            .addInterceptor(AuthInterceptor(object : AuthInterceptor.TokenExpiredHandler {
                override fun onTokenExpired() {
                    Handler(Looper.getMainLooper()).post {
                        // 应用目前已取消账号体系的统一登录，token 过期时仅标记状态
                        PlanetApplication.isExpired = true
                    }
                }
            }))
            .build()
    }

    // MOOC 和 SSO 专用客户端 - 不包含 AuthInterceptor，添加 Cookie 支持
    private val moocClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)  // MOOC 系统可能较慢，增加超时时间
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(UnavailableEndpointInterceptor())
            .addInterceptor(NetworkLogger.getLoggingInterceptor())
            .cookieJar(PersistentCookieJar())
            .build()
    }

    val instanceNewFresh: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(FreshNewsIp)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val instanceUser: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(UserIp)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val instanceSkin: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(ToolsIp)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }


    val instanceSSOAuth: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(SSO_AUTH_URL)
            .client(moocClient)
            .addConverterFactory(retrofit2.converter.scalars.ScalarsConverterFactory.create())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val instanceSSOEhall: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(SSO_EHALL_URL)
            .client(moocClient)
            .addConverterFactory(retrofit2.converter.scalars.ScalarsConverterFactory.create())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
    val instanceComments : Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(CommentsIp)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    /** Legacy-compatible map/calendar endpoint; `.invalid` defaults are blocked before DNS. */
    val instancePlanet: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(PlanetIp)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
}
