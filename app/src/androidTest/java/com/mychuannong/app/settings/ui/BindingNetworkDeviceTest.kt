package com.mychuannong.app.settings.ui

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.textfield.TextInputLayout
import com.mychuannong.app.R
import com.mychuannong.app.common.data.local.mmkv.StudentInfoManager
import com.mychuannong.app.school.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/** Synthetic result-state tests; no school accounts and no production bypasses. */
class BindingNetworkDeviceTest {
    private val inst get() = InstrumentationRegistry.getInstrumentation()
    @Before fun isolatedPackageOnly() = runBlocking {
        assumeTrue(listOf(".bindingtest", ".serviceflowtest", ".accountflowtest").any(inst.targetContext.packageName::endsWith))
        SchoolSyncEngine.clear(); SicauNativeAuth.awaitCleared(); SicauWebVpn.forgetRoute()
        com.mychuannong.app.core.privacy.PrivacyConsentManager.acceptAppConsent(inst.targetContext)
    }
    @Test fun fixedFirstStepHasNoNetworkChoicesOrExplanatoryCopy() {
        ActivityScenario.launch(BindingUserActivity::class.java).use { scenario ->
            snapshot(scenario,"first-step")
            scenario.onActivity { a ->
                assertFalse(a.findViewById<View>(R.id.academic_form).isShown)
                assertTrue(a.findViewById<View>(R.id.connect_webvpn).isShown)
                assertEquals("川农 e 认证登录", a.findViewById<TextView>(R.id.connect_webvpn).text.toString())
                val texts=mutableListOf<String>()
                fun visit(v:View) { if(v is TextView) texts+=v.text.toString(); if(v is ViewGroup) for(i in 0 until v.childCount) visit(v.getChildAt(i)) }
                visit(a.window.decorView)
                assertFalse(texts.any { it.contains("校园网环境") || it.contains("请先完成校园") || it.contains("Keystore") || it.contains("查看隐私说明") })
                val task=ViewModelProvider(a)[NativeBindingViewModel::class.java]
                task.number="synthetic-student";task.password="synthetic-password";task.begin()
                assertFalse(task.state.value.canLogin)
                assertEquals(BindingPhase.ERROR,task.state.value.phase)
                assertFalse(StudentInfoManager.hasCompleteBinding)
            }
        }
    }
    @Test fun successfulVpnRevealsOriginalFormAndRecoveryIsEnabledWithoutCheckbox() {
        ActivityScenario.launch(BindingUserActivity::class.java).use { scenario ->
            scenario.onActivity { a ->
                val task=ViewModelProvider(a)[NativeBindingViewModel::class.java]
                assertTrue(task.startVpnBinding());task.vpnFinished(true)
            }
            inst.waitForIdleSync()
            snapshot(scenario,"academic-step")
            scenario.onActivity { a ->
                assertTrue(a.findViewById<View>(R.id.academic_form).isShown)
                assertTrue(ViewModelProvider(a)[NativeBindingViewModel::class.java].rememberAcademic)
                fun hasCheckbox(v:View):Boolean = v is android.widget.CheckBox ||
                    v is ViewGroup && (0 until v.childCount).any { hasCheckbox(v.getChildAt(it)) }
                assertFalse(hasCheckbox(a.findViewById(R.id.academic_form)))
                a.findViewById<TextView>(R.id.et_student_id).text="synthetic-student"
                a.findViewById<View>(R.id.save_user).performClick()
                assertEquals("请输入教务密码",a.findViewById<TextInputLayout>(R.id.password_input_layout).error.toString())
                assertNull(AcademicCredentialVault.read())
            }
        }
    }
    @Test fun cancellationCannotSkipFirstStepAndRotationPreservesVerifiedStep() {
        ActivityScenario.launch(BindingUserActivity::class.java).use { scenario ->
            scenario.onActivity { a ->
                val task=ViewModelProvider(a)[NativeBindingViewModel::class.java]
                assertTrue(task.startVpnBinding());task.vpnFinished(false)
                assertFalse(task.state.value.canLogin)
                assertTrue(task.startVpnBinding());task.vpnFinished(true)
            }
            scenario.recreate(); inst.waitForIdleSync()
            scenario.onActivity { a ->
                assertTrue(a.findViewById<View>(R.id.academic_form).isShown)
                assertFalse(StudentInfoManager.hasCompleteBinding)
            }
        }
    }
    @Test fun routeAloneIsNotAuthenticationAndMissingRouteRevokesReadyState() {
        inst.runOnMainSync {
            val task=NativeBindingViewModel(checkVpn={ false })
            try {
                SicauWebVpn.discovered(requireNotNull(SicauJiaowuRoute.restore("/https/"+"a".repeat(64))))
                task.prepareAuthentication()
                assertFalse(task.state.value.canLogin)
                task.number="synthetic-student";task.password="synthetic-password"
                assertTrue(task.startVpnBinding());task.vpnFinished(true)
                SicauWebVpn.forgetRoute();task.begin()
                assertFalse(task.state.value.canLogin)
                assertEquals(BindingPhase.ERROR,task.state.value.phase)
            } finally { task.cancel();SicauWebVpn.forgetRoute() }
        }
    }
    @Test fun cancelledFlowIgnoresLateResult() {
        inst.runOnMainSync {
            val task=NativeBindingViewModel()
            assertTrue(task.startVpnBinding());task.cancel();task.vpnFinished(true)
            assertFalse(task.state.value.canLogin)
        }
    }
    private fun snapshot(scenario:ActivityScenario<BindingUserActivity>,name:String) {
        if(InstrumentationRegistry.getArguments().getString("flowScreenshots")!="true")return
        scenario.onActivity { it.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE) }
        inst.waitForIdleSync();inst.uiAutomation.waitForIdle(300,5000)
        Thread.sleep(400) // Present a frame after removing FLAG_SECURE in this isolated test only.
        val bitmap=inst.uiAutomation.takeScreenshot() ?: return
        java.io.File(inst.targetContext.getExternalFilesDir(null),"$name.png").outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
        };bitmap.recycle()
    }
}
