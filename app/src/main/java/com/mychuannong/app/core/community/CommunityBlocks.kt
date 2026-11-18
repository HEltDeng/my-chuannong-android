package com.mychuannong.app.core.community

import android.content.Context
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import com.mychuannong.app.core.PlanetApplication
import com.mychuannong.app.core.network.campustools.AccountSessionState
import com.mychuannong.app.core.network.campustools.CampusToolsSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class BlockedCommunityUser(
    @field:SerializedName("id") val id: String,
    @field:SerializedName("name") val name: String,
)

internal object CommunityBlockRules {
    private fun valid(id: String) = id.isNotBlank() && id.length <= 128 && id.none { it.isISOControl() }
    fun add(existing: List<BlockedCommunityUser>, account: String, author: String, name: String): List<BlockedCommunityUser> {
        require(valid(account) && valid(author) && author != account)
        if (existing.any { it.id == author }) return existing
        val cleanName = name.filterNot { it.isISOControl() }.trim().take(30).ifBlank { "校园用户" }
        return existing + BlockedCommunityUser(author, cleanName)
    }
    fun remove(existing: List<BlockedCommunityUser>, author: String) = existing.filterNot { it.id == author }
}

/** The list is local to this installation and isolated by the signed-in platform account. */
internal object CommunityBlocks {
    private val gson = Gson()
    private val type = object : TypeToken<List<BlockedCommunityUser>>() {}.type
    private val prefs get() = PlanetApplication.appContext.getSharedPreferences("community_blocks_v1", Context.MODE_PRIVATE)
    private val mutableRevision = MutableStateFlow(0)
    val revision = mutableRevision.asStateFlow()
    fun currentAccount(): String? = (CampusToolsSession.state.value as? AccountSessionState.SignedIn)?.user?.id?.takeIf(String::isNotBlank)

    @Synchronized fun users(account: String?): List<BlockedCommunityUser> {
        if (account.isNullOrBlank()) return emptyList()
        return runCatching { gson.fromJson<List<BlockedCommunityUser>>(prefs.getString(account, "[]"), type) }
            .getOrNull().orEmpty().filter { it.id.isNotBlank() }
    }
    @Synchronized fun block(account: String, author: String, name: String) {
        val next = CommunityBlockRules.add(users(account), account, author, name)
        check(prefs.edit().putString(account, gson.toJson(next)).commit())
        mutableRevision.value++
    }
    @Synchronized fun unblock(account: String, author: String) {
        val next = CommunityBlockRules.remove(users(account), author)
        check(prefs.edit().putString(account, gson.toJson(next)).commit())
        mutableRevision.value++
    }
    @Synchronized fun clear(account: String?) {
        if (account.isNullOrBlank()) return
        check(prefs.edit().remove(account).commit())
        mutableRevision.value++
    }
}
