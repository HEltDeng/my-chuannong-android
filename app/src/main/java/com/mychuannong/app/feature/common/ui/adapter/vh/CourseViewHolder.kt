package com.mychuannong.app.feature.common.ui.adapter.vh

import android.annotation.SuppressLint
import android.content.Context
import androidx.recyclerview.widget.RecyclerView
import com.mychuannong.app.databinding.ScoreItemCourseBinding
import com.mychuannong.app.feature.common.data.local.mmkv.ScoreCache
import com.mychuannong.app.feature.common.ui.adapter.model.CourseScore
import com.mychuannong.app.widget.dialog.ScoreDetailDialog

class CourseViewHolder(
    private val binding: ScoreItemCourseBinding,
    private val context: Context,
    private val onDetailClick: (String, String) -> Unit
) : RecyclerView.ViewHolder(binding.root) {

    @SuppressLint("DefaultLocale")
    fun bind(courseScore: CourseScore) {
        binding.apply {
            tvCourseName.text = courseScore.name
            tvScore.text = courseScore.score.toString()
            tvCredit.text = courseScore.creditText?.let { "学分: ${it.ifBlank { "未提供" }}" } ?: courseScore.credit?.let { String.format("学分: %.1f", it) } ?: "学分: 未提供"
            getCredit.text = courseScore.earnedCredit?.let { String.format("绩点: %.1f", it) } ?: "绩点: 未提供"
            tvType.text = courseScore.courseType
            getCredit.visibility = if(courseScore.fromTranscript) android.view.View.GONE else android.view.View.VISIBLE
            tvRemarks.text = courseScore.remarks.takeIf { it.isNotBlank() }?.let { "备注：$it" }.orEmpty()
            tvRemarks.visibility = if(courseScore.remarks.isBlank()) android.view.View.GONE else android.view.View.VISIBLE
            tvNumber.text = courseScore.number.takeIf { it.isNotBlank() }?.let { "课程序号：$it" }.orEmpty()
            tvNumber.visibility = if(courseScore.number.isBlank()) android.view.View.GONE else android.view.View.VISIBLE
        }

        binding.scoreItemLayout.setOnClickListener {
            if(courseScore.fromTranscript) {
                ScoreDetailDialog.showDialog(context, listOf("课程成绩：${courseScore.score}", "课程学分：${courseScore.creditText.orEmpty()}", "课程性质：${courseScore.courseType}", "课程序号：${courseScore.number}", "备注：${courseScore.remarks}").joinToString("\n"), courseScore.name)
                return@setOnClickListener
            }
            // 1. 如果没有 URL，直接弹窗提示（本地处理）
            if (courseScore.pscjUrl == null) {
                ScoreDetailDialog.showDialog(context, "暂无平时成绩", courseScore.name)
                return@setOnClickListener
            }

            // 2. 检查缓存（本地处理，提高性能）
            val cacheDetail = ScoreCache.getGradesDetailByUrl(courseScore.pscjUrl)
            if (cacheDetail.isNotEmpty()) {
                // 有缓存，直接显示
                ScoreDetailDialog.showDialog(context, cacheDetail, courseScore.name)
            } else {
                onDetailClick(courseScore.pscjUrl, courseScore.name)
            }
        }
    }
}
