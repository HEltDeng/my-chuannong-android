package com.mychuannong.app.feature.common.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mychuannong.app.R
import com.mychuannong.app.base.FullScreenActivity
import com.mychuannong.app.common.data.local.mmkv.StudentInfoManager
import com.mychuannong.app.databinding.ActivityScoreInquiryBinding
import com.mychuannong.app.feature.common.contract.ScoreInquiryContract
import com.mychuannong.app.feature.common.ui.adapter.ExamScoreAdapter
import com.mychuannong.app.feature.common.ui.adapter.model.CourseScore
import com.mychuannong.app.feature.common.ui.adapter.model.SemesterGroup
import com.mychuannong.app.feature.common.viewModel.ScoreInquiryViewModel
import com.mychuannong.app.school.*
import kotlinx.coroutines.launch

/** Keep the original toolbar, four summary metrics and expandable course rows. */
class ScoreInquiryActivity : FullScreenActivity<ActivityScoreInquiryBinding>() {
    private val viewModel: ScoreInquiryViewModel by viewModels()
    private val courses by lazy { ExamScoreAdapter(this) { _, _ -> } }
    private val header = TranscriptHeaderAdapter()
    private var preparingWeb = false
    private val login = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        viewModel.processIntent(ScoreInquiryContract.Intent.InitialLoad)
        viewModel.processIntent(ScoreInquiryContract.Intent.RefreshData(true))
    }
    override fun createViewBinding() = ActivityScoreInquiryBinding.inflate(layoutInflater)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val bottom = binding.toolbar.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.toolbar) { view, insets ->
            view.setPadding(view.paddingLeft, insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()).top, view.paddingRight, bottom)
            insets
        }
        binding.bindingBack.setOnClickListener { finish() }
        binding.refresh.setOnClickListener { viewModel.processIntent(ScoreInquiryContract.Intent.RefreshData(true)) }
        binding.ScoreRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.ScoreRecyclerView.adapter = ConcatAdapter(header, courses)
        lifecycleScope.launch {
            viewModel.state.collect { state ->
                binding.loadingLayout.visibility = if(state.isLoading) View.VISIBLE else View.GONE
                binding.refresh.isEnabled = !state.isLoading
                val report = state.transcript
                fun field(name: String) = report?.summary?.find { it.name == name }?.value ?: "--"
                val gpa = report?.summary?.find { it.name in setOf("平均成绩绩点", "平均绩点") }
                binding.gpaLabel.text = gpa?.name ?: "平均成绩绩点"
                binding.totalGpa.text = gpa?.value ?: "--"
                binding.avgScore.text = field("加权平均成绩")
                binding.totalScore.text = field("有效学分")
                binding.totalClass.text = field("算术平均成绩")
                header.show(state, {
                    login.launch(Intent(this@ScoreInquiryActivity, com.mychuannong.app.settings.ui.BindingUserActivity::class.java))
                }, ::openSchoolPage)
                courses.submitList(report?.courses.orEmpty().groupBy { it.term.ifBlank { "个人成绩单" } }.map { (term, rows) ->
                    SemesterGroup(term, Double.NaN, rows.map { row ->
                        CourseScore(row.name, row.grade, null, null, row.nature, null,
                            creditText = row.credit, remarks = row.remarks, number = row.number, fromTranscript = true)
                    }, "学校个人成绩单原始数据")
                })
            }
        }
    }
    override fun onResume() {
        super.onResume()
        viewModel.processIntent(ScoreInquiryContract.Intent.InitialLoad)
    }
    private fun openSchoolPage() {
        if(preparingWeb) return
        preparingWeb = true
        lifecycleScope.launch {
            try {
                when(val result = SchoolSyncEngine.transcriptWebEntry(StudentInfoManager.studentId)) {
                    is SchoolRead.Success -> startActivity(Intent(this@ScoreInquiryActivity, com.mychuannong.app.core.web.ServiceWebActivity::class.java)
                        .putExtra("url_tag", result.value).putExtra("title_tag", "教务成绩查询"))
                    is SchoolRead.Failure -> android.widget.Toast.makeText(this@ScoreInquiryActivity, result.message, android.widget.Toast.LENGTH_LONG).show()
                }
            } finally { preparingWeb = false }
        }
    }

    private class TranscriptHeaderAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        private var state = ScoreInquiryContract.State()
        private var login: () -> Unit = {}
        private var openWeb: () -> Unit = {}
        fun show(value: ScoreInquiryContract.State, onLogin: () -> Unit, onWeb: () -> Unit) {
            state = value; login = onLogin; openWeb = onWeb; notifyItemChanged(0)
        }
        override fun getItemCount() = 1
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val context = parent.context
            fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()
            val card = androidx.cardview.widget.CardView(context).apply {
                radius = dp(10).toFloat()
                setCardBackgroundColor(context.getColor(R.color.color_bg_card))
                layoutParams = RecyclerView.LayoutParams(-1, -2).apply { setMargins(dp(15), dp(10), dp(15), 0) }
            }
            val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(12), dp(14), dp(12)) }
            repeat(4) { index ->
                column.addView(TextView(context).apply {
                    textSize = if(index == 2) 16f else 14f
                    setTextColor(context.getColor(if(index == 1) R.color.color_text_highlight else R.color.color_text_primary))
                    setPadding(0, dp(6), 0, dp(6))
                    layoutParams = LinearLayout.LayoutParams(-1, -2)
                })
            }
            card.addView(column)
            return object : RecyclerView.ViewHolder(card) {}
        }
        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val column = (holder.itemView as ViewGroup).getChildAt(0) as LinearLayout
            (column.getChildAt(0) as TextView).text = if(state.transcript!=null) com.mychuannong.app.school.LearningCacheStatus.text(state.isCached,state.transcript!!.updatedAt) + if(state.isLoading) "\n正在刷新，仍显示已有数据" else "" else state.message
            (column.getChildAt(1) as TextView).apply {
                text = if(state.phase == SyncPhase.EXPIRED) "重新登录教务系统" else "打开教务成绩网页"
                isEnabled = !state.isLoading
                setOnClickListener { if(state.phase == SyncPhase.EXPIRED) login() else openWeb() }
            }
            (column.getChildAt(2) as TextView).apply { text = "有效必修加权排名"; setTypeface(typeface, android.graphics.Typeface.BOLD) }
            val report = state.transcript
            (column.getChildAt(3) as TextView).text = buildList {
                report?.ranking?.forEach { add("${it.name}：${it.value}") }
                if(report == null) add("尚未获取本人排名")
                else if(report.ranking.isEmpty() || report.rankingPhase != SyncPhase.DATA) add(report.rankingMessage)
                if(state.phase == SyncPhase.EMPTY) add("个人成绩单暂无成绩数据")
            }.joinToString("\n")
        }
    }
}
