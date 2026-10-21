package com.mychuannong.app.feature.common.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.gson.JsonObject
import com.mychuannong.app.core.network.campustools.CampusToolsApiClient
import com.mychuannong.app.core.network.campustools.CampusToolsSession
import com.mychuannong.app.core.network.campustools.authenticatedCall

/** Published operational instructions supplement the bundled guide. */
@Composable
internal fun ManagedServiceGuide(id: String) {
    var guide by remember(id) { mutableStateOf<JsonObject?>(null) }
    LaunchedEffect(id) {
        if (id !in setOf("electric", "errand", "logistics_repair")) return@LaunchedEffect
        guide = runCatching {
            if (CampusToolsSession.authorizationToken.isNullOrBlank()) CampusToolsSession.restore()
            authenticatedCall { it.contentGet("services/guides/$id") }.asJsonObject
        }.getOrNull()
    }
    guide?.let { value ->
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(value.get("title")?.asString.orEmpty(), style = MaterialTheme.typography.titleMedium)
                Text(value.get("body")?.asString.orEmpty(), style = MaterialTheme.typography.bodyMedium)
                val image = value.get("image")?.asString.orEmpty()
                if (Regex("/uploads/[0-9a-f-]+\\.(png|jpg|webp)").matches(image)) {
                    coil.compose.AsyncImage(model = CampusToolsApiClient.absoluteUrl(image), contentDescription = "服务指南图片", modifier = Modifier.fillMaxWidth())
                }
                val url = value.get("url")?.asString.orEmpty()
                if (url.startsWith("https://")) {
                    androidx.compose.foundation.text.selection.SelectionContainer {
                        Text(url, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
