package com.mychuannong.app.school

import android.app.Application
import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mychuannong.app.base.ComposeActivity
import com.mychuannong.app.common.data.local.mmkv.StudentInfoManager
import com.mychuannong.app.core.theme.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class TimetableImportState(val term: String = "",
    val drafts: List<TimetableImportRules.Draft> = emptyList(), val busy: Boolean = false,
    val error: String = "", val confirmed: Boolean = false, val savedTerm: String? = null)

class TimetableImportViewModel(application: Application) : AndroidViewModel(application) {
    private val mutable = MutableStateFlow(TimetableImportState())
    val state = mutable.asStateFlow()
    private val account = StudentInfoManager.studentId
    fun loadPage(term:String,html:String) {
        if(mutable.value.drafts.isNotEmpty() || mutable.value.busy) return
        viewModelScope.launch {
            mutable.value=mutable.value.copy(busy=true,error="")
            try {
                require(account.isNotBlank()) { "请先绑定学校账号，再导入自己的课表" }
                val result=withContext(Dispatchers.Default) { TimetableHtmlReader.read(term,html) }
                mutable.value=TimetableImportState(term=result.term,drafts=result.drafts)
            } catch(e:CancellationException) { throw e }
            catch(e:Exception) { mutable.value=TimetableImportState(error=if(e is IllegalArgumentException)e.message.orEmpty() else "当前页面无法读取，请在课表页面重新同步") }
        }
    }
    fun term(value: String) { mutable.value = mutable.value.copy(term = value, confirmed = false) }
    fun confirm(value: Boolean) { mutable.value = mutable.value.copy(confirmed = value) }
    fun edit(index: Int, draft: TimetableImportRules.Draft) {
        mutable.value = mutable.value.copy(drafts = mutable.value.drafts.toMutableList().also { it[index] = draft }, confirmed = false)
    }
    fun remove(index: Int) { mutable.value = mutable.value.copy(drafts = mutable.value.drafts.filterIndexed { i,_ -> i != index }, confirmed = false) }
    fun add() { mutable.value = mutable.value.copy(drafts = mutable.value.drafts + TimetableImportRules.Draft("", "", "", "1", "1", "2", "", "手动补充"), confirmed = false) }
    fun save() {
        val snapshot = mutable.value
        if (snapshot.busy) return
        viewModelScope.launch {
            mutable.value = snapshot.copy(busy = true, error = "")
            try {
                require(snapshot.confirmed && snapshot.drafts.isNotEmpty() && snapshot.drafts.all { it.reviewed }) { "请逐项核对课程，并确认学期和调课安排" }
                val courses = snapshot.drafts.map { it.course(snapshot.term.trim(), account) }
                SchoolSyncEngine.importTimetable(snapshot.term.trim(), account, courses)
                SchoolSyncEngine.saveImportNotes(account,snapshot.term.trim(),snapshot.drafts.map { it.source }.distinct().joinToString("\n\n"))
                mutable.value = mutable.value.copy(busy = false, savedTerm = snapshot.term.trim())
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.value = mutable.value.copy(busy = false, error = if (e is IllegalArgumentException) e.message.orEmpty() else "保存未完成，原有课表已保留，请重试") }
        }
    }
}

class TimetableImportActivity : ComposeActivity() {
    private val model: TimetableImportViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if(savedInstanceState==null && !intent.hasExtra("timetable_html")) { finish();return }
        intent.getStringExtra("timetable_html")?.let { model.loadPage(intent.getStringExtra("term").orEmpty(),it);intent.removeExtra("timetable_html") }
        setComposeContent {
            val state by model.state.collectAsState()
            LaunchedEffect(state.savedTerm) { state.savedTerm?.let { setResult(RESULT_OK, Intent().putExtra("term", it))
                if(intent.getBooleanExtra("open_timetable",false)) startActivity(Intent(this@TimetableImportActivity,com.mychuannong.app.feature.timetable.ui.TimeTableActivity::class.java).putExtra("imported_term",it))
                finish() } }
            Scaffold(containerColor = AppTheme.colors.overviewPageBackgroundColor,
                topBar = { SecondaryScaffoldHeader("核对学校课表", { finish() }) }) { padding ->
                LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        SecondaryCard {
                            Text("已读取学校课表，请核对后保存到本机。", Modifier.padding(vertical = 12.dp))
                            if (state.busy) { LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 12.dp)); Text("正在处理学校课表…") }
                            if (state.error.isNotBlank()) Text(state.error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 12.dp))
                        }
                    }
                    if (state.drafts.isNotEmpty()) {
                        item {
                            SecondaryCard {
                                OutlinedTextField(state.term, model::term, label = { Text("学期，例如 2026-2027-1") }, enabled = !state.busy, modifier = Modifier.fillMaxWidth())
                                Text("读取到 ${state.drafts.size} 条上课安排。请对照学校课表核对；同一门课在不同时段会分开列出。", Modifier.padding(vertical = 12.dp))
                                Text("请特别核对单双周、连堂课，以及下方明细表中的调课或线上教学备注。无法确认的字段需补齐后才能保存。")
                            }
                        }
                        itemsIndexed(state.drafts) { index, draft ->
                            SecondaryCard {
                                Text("安排 ${index + 1}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 8.dp))
                                Text(draft.source, style = MaterialTheme.typography.bodySmall)
                                fun update(next: TimetableImportRules.Draft) = model.edit(index, next.copy(reviewed = false))
                                OutlinedTextField(draft.name, { update(draft.copy(name = it)) }, label = { Text("课程名称") }, enabled = !state.busy, modifier = Modifier.fillMaxWidth())
                                OutlinedTextField(draft.teacher, { update(draft.copy(teacher = it)) }, label = { Text("教师") }, enabled = !state.busy, modifier = Modifier.fillMaxWidth())
                                OutlinedTextField(draft.room, { update(draft.copy(room = it)) }, label = { Text("教室／上课地点") }, enabled = !state.busy, modifier = Modifier.fillMaxWidth())
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(draft.day, { update(draft.copy(day = it)) }, label = { Text("星期 1–7") }, enabled = !state.busy, modifier = Modifier.weight(1f))
                                    OutlinedTextField(draft.start, { update(draft.copy(start = it)) }, label = { Text("开始节") }, enabled = !state.busy, modifier = Modifier.weight(1f))
                                    OutlinedTextField(draft.end, { update(draft.copy(end = it)) }, label = { Text("结束节") }, enabled = !state.busy, modifier = Modifier.weight(1f))
                                }
                                OutlinedTextField(draft.weeks, { update(draft.copy(weeks = it)) }, label = { Text("周次，如 1-16(单周) 或 1,3,5") }, enabled = !state.busy, modifier = Modifier.fillMaxWidth())
                                val validation = runCatching { draft.course(state.term, "validation") }.exceptionOrNull()?.message
                                if (validation != null) Text(validation, color = MaterialTheme.colorScheme.error)
                                Row {
                                    Checkbox(draft.reviewed, { model.edit(index, draft.copy(reviewed = it)) }, enabled = !state.busy && validation == null)
                                    Text("已核对此安排", Modifier.padding(top = 12.dp))
                                    TextButton(onClick = { model.remove(index) }, enabled = !state.busy) { Text("移除") }
                                }
                            }
                        }
                        item { OutlinedButton(model::add, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("补充上课安排") } }
                        item {
                            SecondaryCard {
                                Row {
                                    Checkbox(state.confirmed, model::confirm, enabled = !state.busy)
                                    Text("这是本人的课表，已核对学期、全部课程和调课备注。保存将替换此学期之前导入的课程，保留手动添加的课程。", Modifier.padding(top = 8.dp))
                                }
                                Button(model::save, enabled = !state.busy && state.confirmed && state.drafts.isNotEmpty() && state.drafts.all { it.reviewed }, modifier = Modifier.fillMaxWidth()) { Text("保存到本机课表") }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}
