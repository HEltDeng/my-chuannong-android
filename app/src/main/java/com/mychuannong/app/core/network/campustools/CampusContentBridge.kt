package com.mychuannong.app.core.network.campustools

import android.net.Uri
import android.webkit.JavascriptInterface
import android.webkit.WebResourceResponse
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import java.io.IOException

/** Finite content API for bundled pages. Never exposes platform tokens or school sessions. */
class CampusContentBridge(private val module: String) {
    private val gson = Gson()

    @JavascriptInterface
    fun request(method: String, path: String, body: String): String {
        if (!allows(module, method, path) || body.length > 6 * 1024 * 1024) {
            return failure("FORBIDDEN", "不支持的内容操作")
        }
        return try {
            val data = runBlocking {
                if (CampusToolsSession.authorizationToken.isNullOrBlank()) CampusToolsSession.restore()
                val input = if (body.isBlank()) JsonObject() else JsonParser.parseString(body).asJsonObject
                authenticatedCall { api ->
                    when (method) {
                        "GET" -> api.contentGet(path)
                        "POST" -> api.contentPost(path, input)
                        "PATCH" -> api.contentPatch(path, input)
                        "PUT" -> api.contentPut(path, input)
                        "DELETE" -> api.contentDelete(path)
                        else -> error("Unsupported method")
                    }
                }
            }
            gson.toJson(mapOf("ok" to true, "data" to data))
        } catch (error: CampusToolsApiException) {
            failure(error.errorCode, error.message)
        } catch (_: IOException) {
            failure("OFFLINE", "暂时无法连接校园内容服务，请稍后重试")
        } catch (_: Exception) {
            failure("SERVICE_UNAVAILABLE", "校园内容服务暂不可用，请稍后重试")
        }
    }

    private fun failure(code: String, message: String) = gson.toJson(
        mapOf("ok" to false, "error" to mapOf("code" to code, "message" to message)),
    )

    companion object {
        fun allows(module: String, method: String, path: String): Boolean {
            if (method !in setOf("GET", "POST", "PUT", "PATCH", "DELETE")) return false
            if (path.length > 500 || path.contains("..") || path.contains('%') || path.contains('#') || path.contains('\\')) return false
            val route = path.substringBefore('?')
            if (path.contains('?') && method != "GET") return false
            val query = path.substringAfter('?', "")
            if (query.isNotEmpty() && !Regex("[a-zA-Z0-9_=&-]+").matches(query)) return false
            if (module in setOf("secondhand", "lost-found")) {
                return Regex("community/$module(?:/[a-zA-Z0-9-]+)?").matches(route)
            }
            if (module == "services") {
                return (method == "GET" && Regex("services/(providers|guides)(?:/[a-zA-Z0-9_-]+)?").matches(route)) ||
                    (method in setOf("GET", "POST", "PATCH") && Regex("services/bookings(?:/[a-zA-Z0-9-]+)?").matches(route))
            }
            return false
        }

        /** Images use the fixed product origin; WebViews retain their network sandbox. */
        fun media(uri: Uri): WebResourceResponse? {
            if (uri.scheme != "https" || uri.host != "appassets.androidplatform.net") return null
            val path = uri.path.orEmpty()
            if (!Regex("/uploads/[0-9a-f-]+\\.(png|jpg|webp)").matches(path)) return null
            return runCatching {
                val bytes = CampusToolsApiClient.contentImage(path)
                val mime = when { path.endsWith(".png") -> "image/png"; path.endsWith(".webp") -> "image/webp"; else -> "image/jpeg" }
                WebResourceResponse(mime, null, bytes.inputStream())
            }.getOrElse { WebResourceResponse("text/plain", "UTF-8", 503, "Unavailable", emptyMap(), ByteArray(0).inputStream()) }
        }
    }
}
