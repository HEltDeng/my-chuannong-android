package com.mychuannong.app.feature.common.ui.adapter.vh

import android.content.Context
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mychuannong.app.databinding.ScoreItemSemesterBinding
import com.mychuannong.app.feature.common.ui.adapter.CourseAdapter
import com.mychuannong.app.feature.common.ui.adapter.model.CourseListItem

class SemesterViewHolder(
    private val binding: ScoreItemSemesterBinding,
    private val context: Context,
    private val onToggle: (Int) -> Unit,
    private val onDetailClick: (String, String) -> Unit
) : RecyclerView.ViewHolder(binding.root) {
    private val courseAdapter = CourseAdapter(context, onDetailClick)

    init {
        itemView.setOnClickListener {
            itemView.isClickable = false
            val position = adapterPosition
            if (position != RecyclerView.NO_POSITION) {
                onToggle(position)
            }
            itemView.postDelayed({ itemView.isClickable = true }, 300)
        }

        binding.rvCourses.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = courseAdapter
            isNestedScrollingEnabled = false
        }
    }

    fun bind(item: CourseListItem.SemesterItem) {
        binding.apply {
            tvSemesterName.text = com.mychuannong.app.feature.timetable.ui.AcademicTermDisplayFormatter.format(item.semester.semesterName, com.mychuannong.app.common.data.local.mmkv.StudentInfoManager.admissionYear)
            tvGpa.text = if (item.semester.gpa.isNaN()) "GPA: 口径待确认" else String.format("GPA: %.2f", item.semester.gpa)

            val rotation = if (item.isExpanded) 180f else 0f
            if (ivExpand.rotation != rotation) {
                ivExpand.animate()
                    .rotation(rotation)
                    .setDuration(300)
                    .setInterpolator(FastOutSlowInInterpolator())
                    .start()
            } else {
                ivExpand.rotation = rotation
            }

            if (item.isExpanded) {
                rvCourses.alpha = 0f
                rvCourses.translationY = -80f
                rvCourses.visibility = View.VISIBLE
                rvCourses.animate()
                    .translationY(0f)
                    .alpha(1f)
                    .setDuration(400)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
                courseAdapter.submitList(item.semester.course)
            } else {
                rvCourses.visibility = View.GONE
            }
        }
    }
}