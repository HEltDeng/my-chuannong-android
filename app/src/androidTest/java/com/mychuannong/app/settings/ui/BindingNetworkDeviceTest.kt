package com.mychuannong.app.settings.ui

import android.view.View
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.textfield.TextInputLayout
import com.mychuannong.app.R
import com.mychuannong.app.common.data.local.mmkv.StudentInfoManager
import com.mychuannong.app.school.SicauJiaowuRoute
import com.mychuannong.app.school.SicauWebVpn
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/** UI and result-state regression only: synthetic VPN results, no school requests or credentials. */
class BindingNetworkDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    @Before fun isolatedPackageOnly() {
        assumeTrue(instrumentation.targetContext.packageName.endsWith(".bindingtest"))
        assertFalse(StudentInfoManager.hasCompleteBinding)
    }

    @Test fun networkChoicePrecedesCredentialsAndCampusDoesNotRequireVpn() {
        ActivityScenario.launch(BindingUserActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertEquals(R.id.network_off_campus, activity.findViewById<RadioGroup>(R.id.network_options).checkedRadioButtonId)
                assertFalse(activity.findViewById<View>(R.id.academic_form).isShown)
                assertTrue(activity.findViewById<View>(R.id.connect_webvpn).isShown)
            }
            screenshot("network-choice")
            scenario.onActivity { it.findViewById<View>(R.id.use_direct).performClick() }
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                assertTrue(activity.findViewById<View>(R.id.academic_form).isShown)
                assertTrue(activity.findViewById<View>(R.id.connect_webvpn).isShown)
                assertEquals("校园 VPN 绑定（推荐）", activity.findViewById<TextView>(R.id.connect_webvpn).text.toString())
                assertEquals("请输入教务密码", activity.findViewById<TextInputLayout>(R.id.password_input_layout).hint.toString())
            }
            screenshot("campus-direct")
            scenario.onActivity { activity ->
                activity.findViewById<TextView>(R.id.et_student_id).text = "synthetic-student"
                activity.findViewById<View>(R.id.save_user).performClick()
                assertEquals("请输入教务密码", activity.findViewById<TextInputLayout>(R.id.password_input_layout).error.toString())
                if(!activity.findViewById<RadioButton>(R.id.network_off_campus).isChecked) activity.findViewById<View>(R.id.use_direct).performClick()
            }
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                assertFalse(activity.findViewById<View>(R.id.academic_form).isShown)
                assertTrue(activity.findViewById<View>(R.id.connect_webvpn).isShown)
                assertEquals("校园 VPN 绑定", activity.findViewById<TextView>(R.id.connect_webvpn).text.toString())
                assertFalse(activity.findViewById<View>(R.id.save_user).isEnabled)
            }
            screenshot("off-campus-vpn")
        }
    }

    @Test fun cancelledVpnStaysBlockedAndVerifiedResultSurvivesRecreation() {
        ActivityScenario.launch(BindingUserActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                if(!activity.findViewById<RadioButton>(R.id.network_off_campus).isChecked) activity.findViewById<View>(R.id.use_direct).performClick()
                val task = ViewModelProvider(activity)[NativeBindingViewModel::class.java]
                assertTrue(task.startVpnBinding())
                task.vpnFinished(false)
            }
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                assertFalse(activity.findViewById<View>(R.id.academic_form).isShown)
                assertTrue(activity.findViewById<TextView>(R.id.binding_status).text.startsWith("尚未绑定校园 VPN"))
                val task = ViewModelProvider(activity)[NativeBindingViewModel::class.java]
                assertTrue(task.startVpnBinding())
                task.vpnFinished(true)
            }
            instrumentation.waitForIdleSync()
            scenario.recreate()
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                assertTrue(activity.findViewById<RadioButton>(R.id.network_off_campus).isChecked)
                assertEquals("已绑定校园 VPN，请继续登录", activity.findViewById<TextView>(R.id.binding_status).text.toString())
                assertTrue(activity.findViewById<View>(R.id.academic_form).isShown)
                assertFalse(StudentInfoManager.hasCompleteBinding)
            }
            screenshot("vpn-success-synthetic")
        }
    }

    @Test fun savedRouteDoesNotReplaceVpnVerificationAndMissingRouteRevokesReadyState() {
        instrumentation.runOnMainSync {
            val task = NativeBindingViewModel(checkVpn = { false })
            task.number = "synthetic-student"
            try {
                task.begin()
                assertEquals("请先选择当前网络环境", task.state.value.message)
                SicauWebVpn.discovered(requireNotNull(SicauJiaowuRoute.restore("/https/" + "a".repeat(64))))
                task.selectNetwork(BindingNetwork.OFF_CAMPUS)
                task.begin()
                assertFalse(task.state.value.canLogin)
                assertEquals(BindingPhase.ERROR, task.state.value.phase)
                assertTrue(task.startVpnBinding())
                task.vpnFinished(true)
                SicauWebVpn.forgetRoute()
                task.begin()
                assertFalse(task.state.value.vpnBound)
                assertFalse(task.state.value.canLogin)
                assertEquals(BindingPhase.ERROR, task.state.value.phase)
            } finally {
                task.cancel()
                SicauWebVpn.forgetRoute()
            }
        }
    }

    @Test fun cancelledFlowIgnoresLateVpnResult() {
        instrumentation.runOnMainSync {
            val task = NativeBindingViewModel()
            task.selectNetwork(BindingNetwork.OFF_CAMPUS)
            assertTrue(task.startVpnBinding())
            task.cancel()
            task.vpnFinished(true)
            assertNull(task.state.value.network)
            assertFalse(task.state.value.vpnBound)
            assertFalse(task.state.value.canLogin)
        }
    }

    @Test fun campusCanBindVpnAndExistingVerifiedVpnWinsOverDirectLogin() {
        instrumentation.runOnMainSync {
            val route = requireNotNull(SicauJiaowuRoute.restore("/https/" + "b".repeat(64)))
            val task = NativeBindingViewModel(checkVpn = { true })
            try {
                task.selectNetwork(BindingNetwork.CAMPUS)
                assertTrue(task.startVpnBinding())
                task.vpnFinished(true)
                assertTrue(task.state.value.vpnBound)
                assertTrue(task.state.value.canLogin)
                assertEquals("已绑定校园 VPN，请继续登录", task.state.value.message)
                SicauWebVpn.discovered(route)
                task.selectNetwork(BindingNetwork.OFF_CAMPUS)
                assertTrue(task.state.value.vpnBound)
                task.selectNetwork(BindingNetwork.CAMPUS)
                assertTrue(task.state.value.vpnBound)
                assertSame(route, com.mychuannong.app.school.AcademicRoutePreference.select(
                    campusSelected = true, vpnVerified = task.state.value.vpnBound, vpnRoute = route))
            } finally { task.cancel(); SicauWebVpn.forgetRoute() }
        }
    }

    private fun screenshot(name: String) {
        if (InstrumentationRegistry.getArguments().getString("bindingScreenshots") != "true") return
        instrumentation.waitForIdleSync()
        // Let the compositor present the new layout after radio/visibility animations.
        instrumentation.uiAutomation.waitForIdle(500, 5_000)
        val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: return
        val file = java.io.File(instrumentation.targetContext.getExternalFilesDir(null), "$name.png")
        file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
