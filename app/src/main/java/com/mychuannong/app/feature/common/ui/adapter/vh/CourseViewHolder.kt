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
            tvCredit.text = courseScore.credit?.let { String.format("学分: %.1f", it) } ?: "学分: 未提供"
            getCredit.text = courseScore.earnedCredit?.let { String.format("绩点: %.1f", it) } ?: "绩点: 未提供"
            tvType.text = courseScore.courseType
        }

        binding.scoreItemLayout.setOnClickListener {
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