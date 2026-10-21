package com.mychuannong.app.settings.ui

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.activity.viewModels
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputLayout
import com.mychuannong.app.R
import com.mychuannong.app.base.FullScreenActivity
import com.mychuannong.app.databinding.ActivityBindingUserBinding
import com.mychuannong.app.core.Route
import com.mychuannong.app.core.security.SchoolAccessGate
import com.mychuannong.app.core.MainActivity
import com.mychuannong.app.core.privacy.AccountDataManager
import com.mychuannong.app.school.SicauAdapter
import kotlinx.coroutines.launch

/** Original V1.0.5 XML, inputs, hints, validation and keyboard interactions; Sichuan auth task replaces UserStore. */
class BindingUserActivity:FullScreenActivity<ActivityBindingUserBinding>() {
    companion object {
        const val EXTRA_IS_SWITCH_ACCOUNT="extra_is_switch_account"
        const val EXTRA_REAUTHENTICATE="extra_reauthenticate"
        const val EXTRA_REQUIRED="extra_required_binding"
    }
    private val task:NativeBindingViewModel by viewModels()
    private val username:TextView get()=binding.etStudentId
    private val password:TextView get()=binding.etStudentPassword
    private val save get()=binding.saveUser
    private var openingTimetable=false
    private var editingBinding=false
    private var keyboardVisible=false
    private var scrollBeforeKeyboard=0
    private val vpnLauncher=registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { task.vpnFinished(it.resultCode==RESULT_OK) }
    private val verificationLauncher=registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) {
        task.schoolVerificationFinished(it.resultCode==RESULT_OK)
    }
    private val status:TextView get()=binding.bindingStatus
    override fun createViewBinding()=ActivityBindingUserBinding.inflate(layoutInflater)
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        if (!SchoolAccessGate.enforce(this)) return
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        // School credentials remain in this form's memory; do not offer them to system autofill.
        if(android.os.Build.VERSION.SDK_INT >= 26)
            binding.bindingUserLayout.importantForAutofill=View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        username.isSaveEnabled=false
        password.isSaveEnabled=false

        configureKeyboardInsets()
        editingBinding=savedInstanceState?.getBoolean("editingBinding") ?: (intent.getBooleanExtra(EXTRA_IS_SWITCH_ACCOUNT,false) || intent.getBooleanExtra(EXTRA_REAUTHENTICATE,false))
        binding.boundViewCourses.setOnClickListener { Route.goTimetable(this);finish() }
        binding.boundRelogin.setOnClickListener { editingBinding=true;prepareAuthentication();updateFormState() }
        username.text=task.number;password.text=task.password
        binding.disableAutoLogin.setOnClickListener {
            lifecycleScope.launch {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    com.mychuannong.app.school.SicauJiaowuAuth.disableAutoLogin()
                }
                android.widget.Toast.makeText(this@BindingUserActivity,"已关闭自动登录并删除保存的教务密码",android.widget.Toast.LENGTH_SHORT).show()
            }
        }
        username.doAfterTextChanged { task.number=it.toString();binding.studentIdInputLayout.error=null;updateInputHints();updateFormState() }
        password.doAfterTextChanged { task.password=it.toString();binding.passwordInputLayout.error=null;updateInputHints();updateFormState() }
        username.setOnFocusChangeListener { _,focus ->
            updateInputHints()
            if(!focus && username.text.isBlank()) binding.studentIdInputLayout.error=getString(R.string.binding_student_id_required)
            if(focus) revealFocusedInput()
        }
        password.setOnFocusChangeListener { _,focus -> updateInputHints();if(focus) revealFocusedInput() }
        username.setOnEditorActionListener { _,action,_ -> if(action==EditorInfo.IME_ACTION_NEXT){password.requestFocus();true}else false }
        password.setOnEditorActionListener { _,action,_ -> if(action==EditorInfo.IME_ACTION_DONE){submit();true}else false }
        save.setOnClickListener { submit() }
        binding.schoolVerification.setOnClickListener {
            task.startSchoolVerification()?.let { route ->
                verificationLauncher.launch(android.content.Intent(this, com.mychuannong.app.school.SchoolLoginVerificationActivity::class.java)
                    .putExtra(com.mychuannong.app.school.SchoolLoginVerificationActivity.EXTRA_ROUTE, route.prefix))
            }
        }
        binding.connectWebvpn.setOnClickListener { if(task.startVpnBinding()) openVpn() }
        binding.bindingBack.setOnClickListener { leaveBinding() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { leaveBinding() }
        })
        binding.webLoginBtn.text="解绑学校账号与清除会话"
        binding.webLoginBtn.setOnClickListener {
            if(task.state.value.busy) { task.cancel();prepareAuthentication() }
            else android.app.AlertDialog.Builder(this).setTitle("清除学校会话？").setMessage("清除本应用学校会话及学习缓存；平台账号保持登录。")
                .setPositiveButton("清除") { _,_-> lifecycleScope.launch {
                    task.cancel()
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { AccountDataManager.clearSchoolBinding() }
                    SicauAdapter.clear();editingBinding=false;prepareAuthentication();updateFormState();status.text="学校账号已解绑"
                } }.setNegativeButton("取消",null).show()
        }
        if(!showBoundSummary()) prepareAuthentication()
        updateInputHints();updateFormState()
        lifecycleScope.launch { SchoolAccessGate.state.collect {
            if(!SchoolAccessGate.isAllowed && task.state.value.phase != BindingPhase.CONFIRMED) prepareAuthentication()
            updateFormState()
        } }
        lifecycleScope.launch { task.state.collect { state ->
            val loading=state.phase==BindingPhase.LOADING
            binding.loadingLayout.visibility=if(loading) View.VISIBLE else View.GONE
            binding.bindingUserLayout.visibility=if(loading) View.GONE else View.VISIBLE
            binding.webLoginBtn.text=if(state.busy) "取消当前认证" else "解绑学校账号与清除会话"
            binding.webLoginBtn.visibility=if(state.busy || com.mychuannong.app.common.data.local.mmkv.StudentInfoManager.studentId.isNotBlank()) View.VISIBLE else View.GONE
            val error=state.phase==BindingPhase.ERROR || state.phase==BindingPhase.IDENTITY_PENDING
            status.text=state.message;status.visibility=if(state.message.isBlank() || error) View.GONE else View.VISIBLE
            binding.bindingError.text=if(error) state.message else ""
            binding.bindingError.visibility=if(error) View.VISIBLE else View.GONE
            updateInputHints();updateFormState()
            if(state.phase==BindingPhase.CONFIRMED && SchoolAccessGate.isAllowed && !openingTimetable) {
                openingTimetable=true
                password.text="";task.password=""
                android.widget.Toast.makeText(this@BindingUserActivity,state.message,android.widget.Toast.LENGTH_LONG).show()
                // Binding already verified and saved the school identity; do not log in twice.
                setResult(RESULT_OK)
                if(intent.getBooleanExtra(EXTRA_REQUIRED,false) || callingActivity == null)
                    startActivity(android.content.Intent(this@BindingUserActivity, MainActivity::class.java)
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK))
                finish()
            }
            if(!loading && task.password.isEmpty()) password.text=""
        } }
    }
    private fun submit() {
        if(!validateForm(true)) {
            if(username.text.isBlank()) username.requestFocus() else password.requestFocus()
            revealFocusedInput()
            return
        }
        getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(password.windowToken,0)
        task.begin()
        password.text=""
    }
    private fun showBoundSummary() = SchoolAccessGate.isAllowed &&
        com.mychuannong.app.common.data.local.mmkv.StudentInfoManager.hasCompleteBinding && !editingBinding
    private fun prepareAuthentication() { task.prepareAuthentication() }
    override fun onSaveInstanceState(outState:Bundle) { outState.putBoolean("editingBinding",editingBinding);super.onSaveInstanceState(outState) }
    private fun updateFormState() {
        val bound=showBoundSummary()
        binding.boundSummary.visibility=if(bound) View.VISIBLE else View.GONE
        binding.disableAutoLogin.visibility=if(bound) View.VISIBLE else View.GONE
        binding.bindingCard.visibility=if(bound) View.GONE else View.VISIBLE
        binding.boundAccount.text=com.mychuannong.app.common.data.local.mmkv.StudentInfoManager.greeting + " · " + task.number

        val state=task.state.value
        binding.connectWebvpn.visibility=View.VISIBLE
        binding.connectWebvpn.isEnabled=!state.busy
        binding.connectWebvpn.setText(if(state.vpnBound) R.string.binding_vpn_rebind else R.string.binding_vpn_action)
        binding.bindingStep.text=if(state.canLogin) "第二步 · 登录教务账号" else "第一步 · 川农 e 认证"
        binding.academicForm.visibility=if(state.canLogin) View.VISIBLE else View.GONE
        val error=state.phase==BindingPhase.ERROR || state.phase==BindingPhase.IDENTITY_PENDING
        binding.bindingActions.visibility=if(!bound && (state.canLogin || error)) View.VISIBLE else View.GONE
        binding.schoolVerification.visibility=if(state.schoolVerificationAvailable && !bound) View.VISIBLE else View.GONE
        binding.schoolVerification.isEnabled=!state.busy
        save.visibility=if(state.canLogin) View.VISIBLE else View.GONE
        save.isEnabled=state.canLogin && username.text.isNotBlank() && !state.busy
    }
    private fun openVpn() {
        vpnLauncher.launch(android.content.Intent(this,com.mychuannong.app.school.SicauWebVpnActivity::class.java))
    }
    override fun onDestroy() { if(isFinishing) task.cancel();super.onDestroy() }
    private fun updateInputHints() {
        updateInputHint(
            inputLayout = binding.studentIdInputLayout,
            input = username,
            labelRes = R.string.binding_student_id_label,
            placeholderRes = R.string.binding_student_id_hint
        )
        updateInputHint(
            inputLayout = binding.passwordInputLayout,
            input = password,
            labelRes = R.string.binding_password_label,
            placeholderRes = R.string.binding_password_direct_hint
        )
    }

    private fun updateInputHint(
        inputLayout: TextInputLayout,
        input: TextView,
        @StringRes labelRes: Int,
        @StringRes placeholderRes: Int
    ) {
        val hintRes = if (input.hasFocus() || input.text.isNotEmpty()) {
            labelRes
        } else {
            placeholderRes
        }
        val nextHint = getString(hintRes)
        if (inputLayout.hint != nextHint) {
            inputLayout.hint = nextHint
        }
    }

    private fun validateForm(showErrors: Boolean): Boolean {
        if(!task.state.value.canLogin || task.state.value.busy) return false
        val hasStudentId = username.text.isNotBlank()
        val hasPassword = password.text.isNotBlank()
        if (showErrors) {
            binding.studentIdInputLayout.error = if (hasStudentId) {
                null
            } else {
                getString(R.string.binding_student_id_required)
            }
            binding.passwordInputLayout.error = if (hasPassword) {
                null
            } else {
                getString(R.string.binding_password_required)
            }
        }
        updateFormState()
        return hasStudentId && hasPassword
    }

    private fun leaveBinding() {
        if(keyboardVisible) {
            getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(password.windowToken,0)
            return
        }
        task.cancel()
        if(intent.getBooleanExtra(EXTRA_REQUIRED,false) || !SchoolAccessGate.isAllowed) finishAffinity()
        else finish()
    }

    private fun configureKeyboardInsets() {
        // This page owns all insets; remove FullScreenActivity's separate navigation padding.
        ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { view,insets ->
            view.setPadding(0,0,0,0)
            insets
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view,insets ->
            val bars=insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val ime=insets.getInsets(WindowInsetsCompat.Type.ime())
            val visible=insets.isVisible(WindowInsetsCompat.Type.ime())
            if(visible && !keyboardVisible) scrollBeforeKeyboard=binding.bindingScroll.scrollY
            val justClosed=keyboardVisible && !visible
            keyboardVisible=visible
            view.setPadding(bars.left,0,bars.right,maxOf(bars.bottom,ime.bottom))
            binding.toolbar.setPadding(binding.toolbar.paddingLeft,
                bars.top+(10*resources.displayMetrics.density).toInt(),binding.toolbar.paddingRight,binding.toolbar.paddingBottom)
            binding.bindingScroll.post {
                if(justClosed) binding.bindingScroll.scrollTo(0,scrollBeforeKeyboard)
                else if(visible) revealFocusedInput()
            }
            insets
        }
        binding.bindingScroll.addOnLayoutChangeListener { _,_,_,_,_,_,_,_,_ ->
            if(keyboardVisible) revealFocusedInput()
        }
        ViewCompat.requestApplyInsets(binding.root)
    }

    private fun revealFocusedInput() {
        val field=when {
            password.hasFocus() -> binding.passwordInputLayout
            username.hasFocus() -> binding.studentIdInputLayout
            else -> return
        }
        field.post {
            // Include the label and inline error, not just the EditText's cursor rectangle.
            field.requestRectangleOnScreen(android.graphics.Rect(0,0,field.width,field.height),true)
        }
    }

}
