package com.mychuannong.app.feature.common.ui

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.cardview.widget.CardView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mychuannong.app.R
import com.mychuannong.app.base.FullScreenActivity
import com.mychuannong.app.common.data.local.mmkv.StudentInfoManager
import com.mychuannong.app.core.Route
import com.mychuannong.app.databinding.ActivityScoreInquiryBinding
import com.mychuannong.app.feature.common.contract.ScoreInquiryContract
import com.mychuannong.app.feature.common.data.local.entity.Grade
import com.mychuannong.app.feature.common.data.local.mmkv.ScoreCache
import com.mychuannong.app.feature.common.ui.adapter.ExamScoreAdapter
import com.mychuannong.app.feature.common.ui.adapter.model.CourseScore
import com.mychuannong.app.feature.common.ui.adapter.model.SemesterGroup
import com.mychuannong.app.feature.common.viewModel.ScoreInquiryViewModel
import com.mychuannong.app.widget.dialog.ErrorStuPasswordResponseDialog
import com.mychuannong.app.widget.dialog.NormalResponseDialog
import com.mychuannong.app.widget.dialog.ScoreDetailDialog
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * 成绩查询页面
 */
class ScoreInquiryActivity : FullScreenActivity<ActivityScoreInquiryBinding>() {

    private val viewModel: ScoreInquiryViewModel by viewModels()

    private val recyclerView: RecyclerView by lazy { binding.ScoreRecyclerView }
    private val refresh: ImageView by lazy { binding.refresh }
    private val examScoreAdapter by lazy {
        ExamScoreAdapter(
            this@ScoreInquiryActivity,
            onDetailClick = { pscjUrl, courseName ->
                viewModel.processIntent(
                    ScoreInquiryContract.Intent.FetchScoreDetail(
                        pscjUrl,
                        courseName
                    )
                )
            }
        )
    }
    private val back by lazy { binding.bindingBack }


    private val studentId by lazy { StudentInfoManager.studentId }
    private val studentPassword by lazy { StudentInfoManager.studentPassword }

    override fun createViewBinding(): ActivityScoreInquiryBinding = ActivityScoreInquiryBinding.inflate(layoutInflater)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Native preview stays accessible before school identity is verified.

        ViewCompat.setOnApplyWindowInsetsListener(binding.toolbar){ view, windowInsets->
            val insets=windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                view.paddingLeft,
                insets.top,
                view.paddingRight,
                view.paddingBottom
            )
            WindowInsetsCompat.CONSUMED
        }
        setupRecyclerView()
        initListener()
        initObserve()

        // Load data on startup
        viewModel.processIntent(ScoreInquiryContract.Intent.InitialLoad)
    }

    private fun showLoading() {
        binding.loadingLayout.visibility = View.VISIBLE
        binding.ScoreRecyclerView.visibility = View.GONE
    }

    private fun hideLoading() {
        binding.loadingLayout.visibility = View.GONE
        binding.ScoreRecyclerView.visibility = View.VISIBLE
    }

    private fun initListener() {
        back.setOnClickListener { finish() }
        refresh.setOnClickListener {
            viewModel.processIntent(ScoreInquiryContract.Intent.RefreshData(force = true))
        }
    }

    private fun initObserve() {
        lifecycleScope.launch {
            viewModel.state.collectLatest { state ->
                if (state.isLoading) {
                    showLoading()
                } else {
                    hideLoading()
                    showInfo(state.grades)
                }
            }
        }

        lifecycleScope.launch {
            viewModel.effect.collect { effect ->
                when (effect) {
                    is ScoreInquiryContract.Effect.ShowToast -> showMessage(effect.message)
                    is ScoreInquiryContract.Effect.ShowAuthErrorDialog -> {
                        try {
                            ErrorStuPasswordResponseDialog(
                                this@ScoreInquiryActivity,
                                effect.msg, // "学号或密码错误ʕ⸝⸝⸝˙Ⱉ˙ʔ"
                                effect.title, // "查询失败"
                                {
                                    viewModel.processIntent(
                                        ScoreInquiryContract.Intent.RefreshData(
                                            force = true
                                        )
                                    )
                                }
                            ).show()
                        } catch (e: Exception) { e.printStackTrace() }
                    }

                    is ScoreInquiryContract.Effect.ShowNetErrorDialog -> {
                        try {
                            NormalResponseDialog(
                                this@ScoreInquiryActivity,
                                effect.msg,
                                effect.title
                            ).show()
                        } catch (e: Exception) { e.printStackTrace() }
                    }

                    is ScoreInquiryContract.Effect.ShowDetailDialog -> {
                        ScoreDetailDialog.showDialog(
                            this@ScoreInquiryActivity,
                            effect.detail,
                            effect.title
                        )
                    }
                }
            }
        }
    }

    private fun setupRecyclerView() {
        recyclerView.apply {
            layoutManager = LinearLayoutManager(this@ScoreInquiryActivity)
            adapter = examScoreAdapter
            itemAnimator = DefaultItemAnimator().apply {
                changeDuration = 200
            }
        }
    }

    private fun preprocessGrades(rawData: List<Grade>): List<Grade> {
        return rawData
            .groupBy { it.name }
            .map { (_, grades) ->
                val retakeGrade = grades.find { grade ->
                    grade.upperReItem.isNotBlank() ||
                            grade.property.contains("重修", ignoreCase = true) ||
                            grade.property.contains("补考", ignoreCase = true)
                }

                if (retakeGrade != null) {
                    retakeGrade
                } else {
                    grades.maxByOrNull { it.grade.toDoubleOrNull() ?: 0.0 }!!
                }
            }
    }

    private fun showInfo(rawData: List<Grade>) {
        if (rawData.isEmpty()) {
            binding.totalGpa.text = "--"
            binding.avgScore.text = "尚未同步"
            binding.totalClass.text = "--"
            binding.totalScore.text = "--"
            examScoreAdapter.submitList(emptyList())
            return
        }
        val processedGrades = rawData // Do not impose another school's retake policy.
        ScoreCache.saveGrades(processedGrades) // Ideally caching should be in VM or Repo, but leaving here to minimize diff
        val groupedData = processedGrades.groupBy { it.item }.toSortedMap(compareByDescending { it })
        val semesterGroups = groupedData.map { (semester, grades) ->

            val courseScores = grades.map { grade ->
                CourseScore(
                    name = grade.name,
                    score = grade.grade,
                    credit = grade.score.toDoubleOrNull(),
                    earnedCredit = grade.point.toDoubleOrNull(),
                    courseType = grade.attribute,
                    pscjUrl = grade.pscjUrl
                )
            }

            val semesterGPA = Double.NaN // Sichuan GPA aggregation is not yet evidenced.

            SemesterGroup(
                semesterName = semester,
                gpa = semesterGPA,
                course = courseScores
            )
        }

        val allCourses = processedGrades.size // 课程总数
        val totalCredits = processedGrades.sumOf { it.score.toDoubleOrNull() ?: 0.0 } // 已修总学分



        binding.apply {
            totalGpa.text = "--"
            avgScore.text = "口径待确认"
            totalClass.text = allCourses.toString()
            totalScore.text = if(processedGrades.all { it.score.toDoubleOrNull()!=null }) String.format("%.1f", totalCredits) else "--"
        }

        examScoreAdapter.submitList(semesterGroups)
    }

    private fun showMessage(message: String) {
        Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).apply {
            val cardView = CardView(applicationContext).apply {
                radius = 25f
                cardElevation = 8f
                setCardBackgroundColor(getColor(R.color.color_bg_secondary))
                useCompatPadding = true
            }

            val textView = TextView(applicationContext).apply {
                text = message
                textSize = 17f
                setTextColor(getColor(R.color.color_text_primary))
                gravity = Gravity.CENTER
                setPadding(80, 40, 80, 40)
            }
            cardView.addView(textView)
            setGravity(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, 0, 140)
            view = cardView
            show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}