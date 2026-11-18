package com.mychuannong.app.feature.common.ui

import android.os.Bundle
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.mychuannong.app.core.account.AccountProtectedActivity
import com.mychuannong.app.core.theme.SecondaryScaffoldHeader
import com.mychuannong.app.feature.common.compose_ui.FeatureAvailabilityGate

class ErrandActivity : AccountProtectedActivity() {
    override fun onAccountReady(savedInstanceState: Bundle?) {
        setComposeContent {
            FeatureAvailabilityGate("errand", "跑腿代取", ::finish) {
                Scaffold(
                    containerColor = Color.White,
                    topBar = { SecondaryScaffoldHeader("跑腿代取", ::finish) },
                ) { padding ->
                    Box(
                        Modifier.fillMaxSize().padding(padding),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("即将上线", color = Color(0xFF333333))
                    }
                }
            }
        }
    }
}
