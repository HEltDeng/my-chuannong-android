package com.mychuannong.app.feature.common.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.mychuannong.app.R
import com.mychuannong.app.feature.timetable.viewmodel.TimeTableViewModel

class TimeTableSelectorAdapter(
    private val list: List<String>,
    private val vm: TimeTableViewModel,
    private val isWeekPicker: Boolean,
    private val selectedValue: String?,
    private val itemLabelProvider: (String) -> String,
    private val onItemSelected: () -> Unit,
) :
    RecyclerView.Adapter<TimeTableSelectorAdapter.TimeTableViewHolder>() {
    class TimeTableViewHolder(item: View) : RecyclerView.ViewHolder(item) {
        val label: TextView = item.findViewById(R.id.select)
        val selectionStatus: TextView = item.findViewById(R.id.selectionStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TimeTableViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.selector, parent, false)
        return TimeTableViewHolder(view)
    }

    override fun getItemCount(): Int = list.size

    override fun onBindViewHolder(holder: TimeTableViewHolder, position: Int) {
        val rawValue = list[position]
        val displayLabel = itemLabelProvider(rawValue)
        val isSelected = rawValue == selectedValue
        val context = holder.itemView.context

        holder.label.text = displayLabel
        holder.label.setTextColor(
            ContextCompat.getColor(
                context,
                if (isSelected) R.color.color_text_highlight else R.color.color_text_primary,
            )
        )
        holder.selectionStatus.visibility = if (isSelected) View.VISIBLE else View.GONE
        holder.itemView.isSelected = isSelected
        holder.itemView.setBackgroundColor(
            ContextCompat.getColor(
                context,
                if (isSelected) R.color.color_bg_button_lowlight else R.color.color_bg_recycler_view,
            )
        )
        holder.itemView.contentDescription = if (isSelected) {
            context.getString(R.string.timetable_selection_accessibility, displayLabel)
        } else {
            displayLabel
        }
        holder.itemView.setOnClickListener {
            if (isWeekPicker) {
                vm.selectWeek(rawValue)
            } else {
                vm.selectTerm(rawValue)
            }
            onItemSelected()
        }
    }
}
