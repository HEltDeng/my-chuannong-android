package com.mychuannong.app.feature.buddy.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mychuannong.app.base.ComposeActivity
import com.mychuannong.app.core.theme.AppTheme
import com.mychuannong.app.feature.common.compose_ui.ImeCompatibleTextField
import com.mychuannong.app.freshNews.data.local.mmkv.CampusCommunityLocalStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.tencent.mmkv.MMKV
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class BuddyClubActivity : ComposeActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val activityId = intent.getStringExtra(EXTRA_ACTIVITY_ID)
        val campusMode = intent.getBooleanExtra(EXTRA_CAMPUS_MODE, false)
        setComposeContent { BuddyClubRoute(activityId, campusMode, ::finish) }
    }
    companion object {
        const val EXTRA_ACTIVITY_ID = "buddy_activity_id"
        const val EXTRA_CAMPUS_MODE = "buddy_campus_mode"
        fun intent(context: Context, activityId: String? = null, campusMode: Boolean = false) = Intent(context, BuddyClubActivity::class.java).apply { activityId?.let { putExtra(EXTRA_ACTIVITY_ID, it) }; putExtra(EXTRA_CAMPUS_MODE, campusMode) }
    }
}

enum class BuddyCategory(val label: String) { SPORT("运动"), STUDY("学习/自习"), FOOD("吃饭"), GAME("游戏/桌游"), CAMPUS("社团/校园活动"), OTHER("其他") }
enum class BuddyMode(val label: String) { ANY("不限"), ONLINE("线上"), OFFLINE("线下") }
enum class BuddyStatus(val label: String) { RECRUITING("招募中"), FULL("已满"), FINISHED("已结束"), CANCELLED("已取消") }
data class LocalBuddyActivity(val id: String, val title: String, val category: BuddyCategory, val mode: BuddyMode, val date: String, val time: String, val location: String, val targetPeople: Int, val description: String = "", val joinedCount: Int = 0, val joinedByDevice: Boolean = false, val status: BuddyStatus = BuddyStatus.RECRUITING, val createdAtMillis: Long = System.currentTimeMillis())

object BuddyClubLocalStore {
    private const val CACHE_ID = "buddy_club_local_v1"
    private const val KEY = "activities_v1"
    private val gson = Gson(); private val type = object : TypeToken<List<LocalBuddyActivity>>() {}.type
    private val mmkv by lazy { MMKV.mmkvWithID(CACHE_ID) }
    private val _activities = MutableStateFlow(load())
    val activities: StateFlow<List<LocalBuddyActivity>> = _activities.asStateFlow()
    fun create(title: String, category: BuddyCategory, mode: BuddyMode, date: String, time: String, location: String, target: Int, description: String): LocalBuddyActivity? {
        if (title.isBlank() || location.isBlank() || time.isBlank() || target !in 2..30) return null
        // The publisher occupies the first local place. This keeps the device-only demo coherent
        // and lets a two-person activity demonstrate the full state after one local signup.
        val activity = LocalBuddyActivity(UUID.randomUUID().toString(), title.trim().take(40), category, mode, date, time, location.trim().take(60), target, description.trim().take(300), joinedCount = 1)
        save(listOf(activity) + _activities.value)
        CampusCommunityLocalStore.publish(activity.title, "${activity.category.label} · ${activity.date} ${activity.time} · ${activity.location}\n目标 ${activity.targetPeople} 人\n${activity.description}".trim(), "搭子社", linkedActivityId = activity.id)
        return activity
    }
    fun join(id: String) {
        val current = _activities.value
        val index = current.indexOfFirst { it.id == id }
        if (index < 0) return
        val activity = current[index]
        if (activity.status != BuddyStatus.RECRUITING || activity.joinedByDevice || activity.joinedCount >= activity.targetPeople) return
        val joinedCount = activity.joinedCount + 1
        save(current.toMutableList().apply {
            this[index] = activity.copy(
                joinedCount = joinedCount,
                joinedByDevice = true,
                status = if (joinedCount >= activity.targetPeople) BuddyStatus.FULL else BuddyStatus.RECRUITING
            )
        })
    }
    fun cancelJoin(id: String) {
        val current = _activities.value
        val index = current.indexOfFirst { it.id == id }
        if (index < 0) return
        val activity = current[index]
        if (!activity.joinedByDevice) return
        save(current.toMutableList().apply {
            this[index] = activity.copy(
                joinedCount = (activity.joinedCount - 1).coerceAtLeast(0),
                joinedByDevice = false,
                status = if (activity.status == BuddyStatus.FULL) BuddyStatus.RECRUITING else activity.status
            )
        })
    }
    fun close(id: String, cancelled: Boolean) = update(id) { it.copy(status = if (cancelled) BuddyStatus.CANCELLED else BuddyStatus.FINISHED) }
    fun find(id: String): LocalBuddyActivity? = activities.value.firstOrNull { it.id == id }
    private fun update(id: String, block: (LocalBuddyActivity) -> LocalBuddyActivity?) {
        val current = _activities.value
        val index = current.indexOfFirst { it.id == id }
        if (index < 0) return
        val replacement = block(current[index]) ?: return
        if (replacement == current[index]) return
        save(current.toMutableList().apply { this[index] = replacement })
    }
    private fun load() = runCatching { gson.fromJson<List<LocalBuddyActivity>>(mmkv.decodeString(KEY), type).orEmpty().filter { it.id.isNotBlank() && it.title.isNotBlank() && it.targetPeople in 2..30 } }.getOrDefault(emptyList())
    private fun save(value: List<LocalBuddyActivity>) { check(mmkv.encode(KEY, gson.toJson(value))); _activities.value = value }
}

@Composable private fun BuddyClubRoute(openId: String?, campusMode: Boolean, onBack: () -> Unit) {
    val activities by BuddyClubLocalStore.activities.collectAsState()
    var page by remember(openId) { mutableStateOf(if (openId == null) BuddyPage.List else BuddyPage.Detail(openId)) }
    when (val current = page) {
        BuddyPage.List -> BuddyList(campusMode, activities, onBack, { page = BuddyPage.Publish(campusMode) }, { page = BuddyPage.Detail(it) })
        // Read the collected list here so detail state is recomposed immediately after a local
        // signup, cancellation, or activity status change.
        is BuddyPage.Detail -> BuddyDetail(activities.firstOrNull { it.id == current.id }, { page = BuddyPage.List }, BuddyClubLocalStore::join, BuddyClubLocalStore::cancelJoin, BuddyClubLocalStore::close)
        is BuddyPage.Publish -> BuddyPublish(campusMode, { page = BuddyPage.List }, { page = BuddyPage.Detail(it.id) })
    }
}
private sealed interface BuddyPage { data object List : BuddyPage; data class Detail(val id: String) : BuddyPage; data class Publish(val campusMode: Boolean) : BuddyPage }

@OptIn(ExperimentalMaterial3Api::class) @Composable private fun BuddyScaffold(title: String, onBack: () -> Unit, content: @Composable (Modifier) -> Unit) { val c = AppTheme.colors; Scaffold(containerColor = c.overviewPageBackgroundColor, topBar = { TopAppBar(title = { Text(title, fontWeight = FontWeight.Bold, color = c.primaryTextColor) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "返回", tint = c.primaryTextColor) } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = c.overviewPageBackgroundColor)) }) { p -> content(Modifier.padding(p).padding(horizontal = 16.dp)) } }
@Composable private fun BuddyList(campusMode: Boolean, all: List<LocalBuddyActivity>, onBack: () -> Unit, onPublish: () -> Unit, onOpen: (String) -> Unit) { var category by remember { mutableStateOf<BuddyCategory?>(if (campusMode) BuddyCategory.CAMPUS else null) }; val title = if (campusMode) "校园活动" else "搭子社"; BuddyScaffold(title, onBack) { m -> val shown = all.filter { category == null || it.category == category }; Scaffold(modifier = m.fillMaxSize(), containerColor = AppTheme.colors.overviewPageBackgroundColor, floatingActionButton = { FloatingActionButton(onClick = onPublish, containerColor = AppTheme.colors.commonColor) { Icon(Icons.Rounded.Add, "发布搭子活动", tint = AppTheme.colors.textButtonColor) } }) { p -> Column(Modifier.padding(p), verticalArrangement = Arrangement.spacedBy(10.dp)) { Text(if (campusMode) "本地校园活动原型，不代表校方通知。" else "活动仅保存在当前设备，不含联系方式或聊天。", color = AppTheme.colors.secondaryTextColor); LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { items(listOf("一起玩", "出去玩", "运动搭子", "校园组队")) { label -> FilterChip(false, { category = when (label) { "运动搭子" -> BuddyCategory.SPORT; "校园组队" -> BuddyCategory.CAMPUS; else -> null } }, { Text(label) }) } }; LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { item { FilterChip(category == null, { category = null }, { Text("全部") }) }; items(BuddyCategory.entries.toList()) { item -> FilterChip(category == item, { category = item }, { Text(item.label) }) } }; if (shown.isEmpty()) { Spacer(Modifier.height(28.dp)); Text("还没有本地活动，发布第一条吧。", color = AppTheme.colors.secondaryTextColor) } else LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp)) { items(shown, key = LocalBuddyActivity::id) { BuddyCard(it) { onOpen(it.id) } } } } } } }
@Composable private fun BuddyCard(a: LocalBuddyActivity, onClick: () -> Unit) = Card(Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = AppTheme.colors.bgCardColor)) { Column(Modifier.padding(16.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Rounded.Groups, null, tint = AppTheme.colors.commonColor); Spacer(Modifier.width(8.dp)); Text(a.category.label, color = AppTheme.colors.commonColor, fontSize = 12.sp); Spacer(Modifier.weight(1f)); Text(a.status.label, color = AppTheme.colors.secondaryTextColor, fontSize = 12.sp) }; Spacer(Modifier.height(8.dp)); Text(a.title, fontWeight = FontWeight.Bold, color = AppTheme.colors.primaryTextColor, fontSize = 17.sp); Text("${a.date} ${a.time} · ${a.location}", color = AppTheme.colors.secondaryTextColor, fontSize = 13.sp); Text("${a.joinedCount}/${a.targetPeople} 人 · ${a.mode.label}", color = AppTheme.colors.secondaryTextColor, fontSize = 13.sp) } }
@Composable private fun BuddyPublish(campusMode: Boolean, onBack: () -> Unit, onPublished: (LocalBuddyActivity) -> Unit) { var title by remember { mutableStateOf("") }; var location by remember { mutableStateOf("") }; var desc by remember { mutableStateOf("") }; var target by remember { mutableStateOf("2") }; var category by remember { mutableStateOf(if (campusMode) BuddyCategory.CAMPUS else BuddyCategory.SPORT) }; var mode by remember { mutableStateOf(BuddyMode.ANY) }; var date by remember { mutableStateOf(LocalDate.now().toString()) }; var time by remember { mutableStateOf("19:00") }; val dates = remember { (0L..6L).map { LocalDate.now().plusDays(it).toString() } }; val scrollState = rememberScrollState(); BuddyScaffold(if (campusMode) "发布校园活动" else "发布搭子活动", onBack) { m -> Column(m.fillMaxSize().imePadding().verticalScroll(scrollState).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { ImeCompatibleTextField(title, { title = it.take(40) }, label = "活动标题", modifier = Modifier.fillMaxWidth()); Text("活动分类"); LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { items(BuddyCategory.entries.toList()) { item -> FilterChip(category == item, { category = item }, { Text(item.label) }) } }; Text("线上 / 线下"); Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { BuddyMode.entries.forEach { FilterChip(mode == it, { mode = it }, { Text(it.label) }) } }; Text("日期"); LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { items(dates) { item -> FilterChip(date == item, { date = item }, { Text(item.removePrefix(LocalDate.now().year.toString() + "-")) }) } }; Text("时间"); LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { items(listOf("12:00", "18:00", "19:00", "20:00")) { item -> FilterChip(time == item, { time = item }, { Text(item) }) } }; ImeCompatibleTextField(location, { location = it.take(60) }, label = "地点", modifier = Modifier.fillMaxWidth()); ImeCompatibleTextField(target, { target = it.filter(Char::isDigit).take(2) }, label = "目标人数（2-30）", modifier = Modifier.fillMaxWidth(), numeric = true); ImeCompatibleTextField(desc, { desc = it.take(300) }, label = "活动说明（可选）", modifier = Modifier.fillMaxWidth(), singleLine = false, minLines = 3, maxLines = 6); Button(onClick = { BuddyClubLocalStore.create(title, category, mode, date, time, location, target.toIntOrNull() ?: 0, desc)?.let(onPublished) }, enabled = title.isNotBlank() && location.isNotBlank() && (target.toIntOrNull() ?: 0) in 2..30, modifier = Modifier.fillMaxWidth()) { Text("本地发布") }; Text("不收集或公开联系方式；发布后会在校园频道“搭子社”分类显示入口。", color = AppTheme.colors.secondaryTextColor, fontSize = 12.sp) } } }
@Composable private fun BuddyDetail(a: LocalBuddyActivity?, onBack: () -> Unit, onJoin: (String) -> Unit, onCancelJoin: (String) -> Unit, onClose: (String, Boolean) -> Unit) { var confirmClose by remember { mutableStateOf(false) }; BuddyScaffold("活动详情", onBack) { m -> if (a == null) Column(m) { Text("活动不存在或本地数据已清除") } else Column(m.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) { Text(a.title, fontSize = 23.sp, fontWeight = FontWeight.Bold, color = AppTheme.colors.primaryTextColor); Text("${a.category.label} · ${a.mode.label}\n${a.date} ${a.time}\n地点：${a.location}\n人数：${a.joinedCount}/${a.targetPeople}\n状态：${a.status.label}", color = AppTheme.colors.primaryTextColor, lineHeight = 23.sp); if (a.description.isNotBlank()) Text(a.description, color = AppTheme.colors.secondaryTextColor); when { a.joinedByDevice -> OutlinedButton(onClick = { onCancelJoin(a.id) }, modifier = Modifier.fillMaxWidth()) { Text("取消报名") }; a.status == BuddyStatus.RECRUITING -> Button(onClick = { onJoin(a.id) }, modifier = Modifier.fillMaxWidth()) { Text("报名参加") }; else -> Text("当前状态不可报名", color = AppTheme.colors.secondaryTextColor) }; OutlinedButton(onClick = { confirmClose = true }, modifier = Modifier.fillMaxWidth()) { Text("标记已结束 / 取消") }; Text("本地演示：报名人数只代表当前设备操作，不代表真实人数。", color = AppTheme.colors.secondaryTextColor, fontSize = 12.sp); if (confirmClose) AlertDialog(onDismissRequest = { confirmClose = false }, title = { Text("结束活动？") }, text = { Text("此操作只更改本地状态。") }, confirmButton = { Button(onClick = { onClose(a.id, false); confirmClose = false }) { Text("已结束") } }, dismissButton = { OutlinedButton(onClick = { onClose(a.id, true); confirmClose = false }) { Text("取消活动") } }) } } }
