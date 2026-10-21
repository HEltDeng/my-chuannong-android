package com.mychuannong.app.feature.common.ui

import android.annotation.SuppressLint
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mychuannong.app.R
import com.mychuannong.app.base.FullScreenActivity
import com.mychuannong.app.common.data.local.mmkv.StudentInfoManager
import com.mychuannong.app.core.Route
import com.mychuannong.app.databinding.ActivityExamArrangementBinding
import com.mychuannong.app.feature.common.contract.ExamArrangementContract
import com.mychuannong.app.feature.common.ui.adapter.ExamArrangementAdapter
import com.mychuannong.app.feature.common.ui.adapter.model.Exam
import com.mychuannong.app.feature.common.viewModel.ExamArrangementViewModel
import com.mychuannong.app.widget.dialog.ErrorStuPasswordResponseDialog
import com.mychuannong.app.widget.dialog.NormalResponseDialog
import com.mychuannong.app.widget.view.CustomToast
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * 考试安排查询
 */
class ExamArrangementActivity : FullScreenActivity<ActivityExamArrangementBinding>() {

    private val viewModel: ExamArrangementViewModel by viewModels()

    private val examRecyclerView: RecyclerView by lazy { binding.recyclerView }
    private val back: ImageView by lazy { binding.bindingBack }
    private val refresh: ImageView by lazy { binding.refresh }
    private val studentId by lazy { StudentInfoManager.studentId }
    private var adapter: ExamArrangementAdapter? = null

    private fun showLoading() {
        binding.loadingLayout.visibility = View.VISIBLE
        examRecyclerView.visibility = View.GONE
        binding.emptyState.visibility = View.GONE
    }

    private fun hideLoading() {
        binding.loadingLayout.visibility = View.GONE
        examRecyclerView.visibility = View.VISIBLE
    }

    override fun createViewBinding(): ActivityExamArrangementBinding =
        ActivityExamArrangementBinding.inflate(layoutInflater)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ViewCompat.setOnApplyWindowInsetsListener(binding.toolbar) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(
                view.paddingLeft,
                insets.top + (10 * resources.displayMetrics.density).toInt(),
                view.paddingRight,
                view.paddingBottom
            )
            WindowInsetsCompat.CONSUMED
        }

        examRecyclerView.layoutManager = LinearLayoutManager(this)
        // Initialize adapter with empty list
        adapter = ExamArrangementAdapter(mutableListOf())
        examRecyclerView.adapter = adapter
        back.setOnClickListener { finish() }
        refresh.setOnClickListener { refreshData() }
        binding.pullRefresh.setColorSchemeResources(R.color.binding_green)
        binding.pullRefresh.setOnChildScrollUpCallback { _, _ -> examRecyclerView.canScrollVertically(-1) }
        binding.pullRefresh.setOnRefreshListener { refreshData() }
        viewModel.processIntent(ExamArrangementContract.Intent.InitialLoad)
        initObserve()
    }

    private fun initObserve() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.state.collectLatest { state ->
                        binding.pullRefresh.isRefreshing=state.isLoading
                        refresh.isEnabled=!state.isLoading
                        binding.cacheStatus.text=listOfNotNull(state.lastSuccess?.let {
                            "最后更新："+java.text.SimpleDateFormat("yyyy-MM-dd HH:mm",java.util.Locale.CHINA).format(java.util.Date(it))
                        }, if(state.isLoading) "正在刷新，仍显示上次数据" else state.statusMessage).joinToString("\n")
                        if (state.isLoading && state.exams.isEmpty()) {
                            showLoading()
                        } else {
                            hideLoading()
                            updateExamList(state.exams)
                            binding.emptyState.text = state.statusMessage
                            binding.emptyState.visibility = if (state.exams.isEmpty()) View.VISIBLE else View.GONE
                        }
                    }
                }

                launch {
                    viewModel.effect.collect { effect ->
                        when (effect) {
                            is ExamArrangementContract.Effect.ShowToast -> {
                                CustomToast.showMessage(
                                    this@ExamArrangementActivity,
                                    effect.message
                                )
                            }

                            is ExamArrangementContract.Effect.ShowErrorDialog -> {
                                NormalResponseDialog(
                                    this@ExamArrangementActivity,
                                    effect.message,
                                    "查询失败"
                                ).show()
                            }

                            is ExamArrangementContract.Effect.ShowBindingRequired -> {
                                ErrorStuPasswordResponseDialog(
                                    this@ExamArrangementActivity,
                                    effect.message,
                                    "查询失败"
                                ) { refreshData() }.show()
                            }
                        }
                    }
                }
            }
        }
    }



    private fun refreshData() {
        if (StudentInfoManager.hasCompleteBinding) {
            viewModel.processIntent(
                ExamArrangementContract.Intent.LoadExamArrangement(
                    getCurrentTerm()
                )
            )
        } else {
            binding.pullRefresh.isRefreshing=false
            binding.emptyState.text = "请先绑定学校账号，或重新完成学校验证"
            binding.emptyState.visibility = View.VISIBLE
            showMessage("请先绑定学校账号，或重新完成学校验证")
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun updateExamList(exams: List<Exam>) {
        // Update adapter
        adapter?.updateData(exams.toMutableList())
        adapter?.notifyDataSetChanged()
    }

    private fun getCurrentTerm(): String {
        val calendar = Calendar.getInstance()
        val currentYear = calendar.get(Calendar.YEAR)
        val currentMonth = calendar.get(Calendar.MONTH) + 1
        return when {
            currentMonth >= 9 -> "$currentYear-${currentYear + 1}-1"  // 第一学期
            currentMonth >= 2 -> "${currentYear - 1}-${currentYear}-2"  // 第二学期
            else -> "${currentYear - 1}-${currentYear}-1"  // 上学年第一学期
        }
    }

    private fun showMessage(msg: String) {
        Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).apply {
            val cardView = CardView(applicationContext).apply {
                radius = 25f
                cardElevation = 8f
                setCardBackgroundColor(getColor(R.color.color_bg_secondary))
                useCompatPadding = true
            }

            val textView = TextView(applicationContext).apply {
                text = msg
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
