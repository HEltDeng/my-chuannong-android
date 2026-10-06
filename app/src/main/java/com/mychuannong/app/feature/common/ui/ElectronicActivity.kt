package com.mychuannong.app.feature.common.ui

import android.os.Bundle
import androidx.activity.viewModels
import com.mychuannong.app.base.ComposeActivity
import com.mychuannong.app.feature.common.compose_ui.ElectronicScreen
import com.mychuannong.app.feature.common.viewModel.ElectronicViewModel

class ElectronicActivity : ComposeActivity() {
    private val viewModel: ElectronicViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setComposeContent { ElectronicScreen(viewModel, ::finish) }
    }
}
