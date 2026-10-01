package com.mychuannong.app.widget.dialog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mychuannong.app.R
import com.mychuannong.app.feature.common.ui.adapter.TimeTableSelectorAdapter
import com.mychuannong.app.feature.timetable.viewmodel.TimeTableViewModel
import com.mychuannong.app.widget.view.DividerItemDecoration
import com.mychuannong.app.widget.view.MaxHeightLinearLayout
import com.google.android.material.bottomsheet.BottomSheetDialogFragment


class TimetableWheelBottomDialog(
    private val vm: TimeTableViewModel,
    private val maxHeight: Int,
    private val isWeekPicker: Boolean,
) : BottomSheetDialogFragment() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: TimeTableSelectorAdapter
    private var title: String? = null
    private var item: List<String> = emptyList()
    private var selectedValue: String? = null
    private var itemLabelProvider: (String) -> String = { it }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.select_in_timetable, container, false)
        recyclerView = view.findViewById(R.id.selectRecyclerTimetable)
        view.findViewById<TextView>(R.id.selectTimetableTitle).text = title
            ?: getString(
                if (isWeekPicker) R.string.timetable_select_week else R.string.timetable_select_term
            )

        val maxHeightLinearLayout = view.findViewById<MaxHeightLinearLayout>(R.id.maxHeightLayout)
        maxHeightLinearLayout.setMaxHeight(maxHeight)
        adapter = TimeTableSelectorAdapter(
            item,
            vm,
            isWeekPicker,
            selectedValue,
            itemLabelProvider,
        ) {
            dismiss()
        }
        val layoutManager = LinearLayoutManager(context)
        recyclerView.layoutManager = layoutManager
        recyclerView.adapter = adapter
        recyclerView.addItemDecoration(DividerItemDecoration())
        val selectedIndex = item.indexOf(selectedValue).takeIf { it >= 0 } ?: 0
        if (item.isNotEmpty()) {
            recyclerView.post {
                layoutManager.scrollToPositionWithOffset(selectedIndex, 0)
            }
        }
        return view
    }

    fun setItem(list: List<String>) {
        item = list
    }

    fun setTitle(title: String) {
        this.title = title
    }

    fun setSelectedValue(value: String) {
        selectedValue = value
    }

    fun setItemLabelProvider(provider: (String) -> String) {
        itemLabelProvider = provider
    }
}
