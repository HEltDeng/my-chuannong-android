package com.mychuannong.app.settings.ui

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.activity.viewModels
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputLayout
import com.mychuannong.app.R
import com.mychuannong.app.base.FullScreenActivity
import com.mychuannong.app.databinding.ActivityBindingUserBinding
import com.mychuannong.app.core.Route
import com.mychuannong.app.core.privacy.ReleaseDocumentType
import com.mychuannong.app.core.privacy.AccountDataManager
import com.mychuannong.app.school.SicauAdapter
import kotlinx.coroutines.launch

/** Original V1.0.5 XML, inputs, hints, validation and keyboard interactions; Sichuan auth task replaces UserStore. */
class BindingUserActivity:FullScreenActivity<ActivityBindingUserBinding>() {
    companion object {
        const val EXTRA_IS_SWITCH_ACCOUNT="extra_is_switch_account"
        const val EXTRA_REAUTHENTICATE="extra_reauthenticate"
    }
    private val task:NativeBindingViewModel by viewModels()
    private val username:TextView get()=binding.etStudentId
    private val password:TextView get()=binding.etStudentPassword
    private val save get()=binding.saveUser
    private var openingTimetable=false
    private var editingBinding=false
    private val vpnLauncher=registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { task.vpnFinished(it.resultCode==RESULT_OK) }
    private val status:TextView get()=binding.bindingStatus
    override fun createViewBinding()=ActivityBindingUserBinding.inflate(layoutInflater)
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        // School credentials remain in this form's memory; do not offer them to system autofill.
        if(android.os.Build.VERSION.SDK_INT >= 26)
            binding.bindingUserLayout.importantForAutofill=View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        username.isSaveEnabled=false
        password.isSaveEnabled=false

        ViewCompat.setOnApplyWindowInsetsListener(binding.toolbar) { view,insets ->
            val bars=insets.getInsets(WindowInsetsCompat.Type.systemBars());view.setPadding(view.paddingLeft,bars.top+(10*resources.displayMetrics.density).toInt(),view.paddingRight,view.paddingBottom);insets
        }
        editingBinding=savedInstanceState?.getBoolean("editingBinding") ?: (intent.getBooleanExtra(EXTRA_IS_SWITCH_ACCOUNT,false) || intent.getBooleanExtra(EXTRA_REAUTHENTICATE,false))
        binding.boundViewCourses.setOnClickListener { Route.goTimetable(this);finish() }
        binding.boundRelogin.setOnClickListener { editingBinding=true;startPreferredNetwork();updateFormState() }
        binding.useDirect.setOnClickListener {
            task.selectNetwork(if(task.state.value.network==BindingNetwork.CAMPUS) BindingNetwork.OFF_CAMPUS else BindingNetwork.CAMPUS)
        }
        username.text=task.number;password.text=task.password
        username.doAfterTextChanged { task.number=it.toString();binding.studentIdInputLayout.error=null;updateInputHints();updateFormState() }
        password.doAfterTextChanged { task.password=it.toString();binding.passwordInputLayout.error=null;updateInputHints();updateFormState() }
        username.setOnFocusChangeListener { _,focus -> updateInputHints();if(!focus && username.text.isBlank()) binding.studentIdInputLayout.error=getString(R.string.binding_student_id_required) }
        password.setOnFocusChangeListener { _,_ -> updateInputHints() }
        username.setOnEditorActionListener { _,action,_ -> if(action==EditorInfo.IME_ACTION_NEXT){password.requestFocus();true}else false }
        password.setOnEditorActionListener { _,action,_ -> if(action==EditorInfo.IME_ACTION_DONE){submit();true}else false }
        save.setOnClickListener { submit() }
        binding.connectWebvpn.setOnClickListener { if(task.startVpnBinding()) openVpn() }
        binding.networkOptions.setOnCheckedChangeListener { _,checkedId ->
            when(checkedId) {
                R.id.network_campus -> task.selectNetwork(BindingNetwork.CAMPUS)
                R.id.network_off_campus -> task.selectNetwork(BindingNetwork.OFF_CAMPUS)
            }
            binding.passwordInputLayout.error=null
            updateInputHints();updateFormState()
        }
        binding.bindingBack.setOnClickListener { task.cancel();finish() }
        binding.bindingPrivacyPolicyLink.setOnClickListener { Route.goReleaseInformation(this,ReleaseDocumentType.PRIVACY) }
        binding.webLoginBtn.text="解绑学校账号与清除会话"
        binding.webLoginBtn.setOnClickListener {
            if(task.state.value.busy) task.cancel()
            else android.app.AlertDialog.Builder(this).setTitle("清除学校会话？").setMessage("清除本应用学校会话及学习缓存；平台账号保持登录。")
                .setPositiveButton("清除") { _,_-> lifecycleScope.launch {
                    task.cancel()
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { AccountDataManager.clearSchoolBinding() }
                    SicauAdapter.clear();editingBinding=false;startPreferredNetwork();updateFormState();status.text="学校账号已解绑"
                } }.setNegativeButton("取消",null).show()
        }
        if(!showBoundSummary()) startPreferredNetwork()
        updateInputHints();updateFormState()
        lifecycleScope.launch { task.state.collect { state ->
            val loading=state.phase==BindingPhase.LOADING
            binding.loadingLayout.visibility=if(loading) View.VISIBLE else View.GONE
            binding.bindingUserLayout.visibility=if(loading) View.GONE else View.VISIBLE
            binding.webLoginBtn.text=if(state.busy) "取消当前认证" else "解绑学校账号与清除会话"
            binding.webLoginBtn.visibility=if(state.busy || com.mychuannong.app.common.data.local.mmkv.StudentInfoManager.studentId.isNotBlank()) View.VISIBLE else View.GONE
            status.text=state.message;status.visibility=if(state.message.isBlank()) View.GONE else View.VISIBLE
            updateInputHints();updateFormState()
            if(state.phase==BindingPhase.CONFIRMED && !openingTimetable) {
                openingTimetable=true
                password.text="";task.password=""
                android.widget.Toast.makeText(this@BindingUserActivity,state.message,android.widget.Toast.LENGTH_LONG).show()
                // Binding ends at the verified native jiaowu session; course adaptation is separate.
                setResult(RESULT_OK);finish()
            }
            if(!loading && task.password.isEmpty()) password.text=""
        } }
    }
    private fun submit() {
        if(!validateForm(true)) return
        getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(password.windowToken,0)
        task.begin()
    }
    private fun showBoundSummary() = com.mychuannong.app.common.data.local.mmkv.StudentInfoManager.hasCompleteBinding && !editingBinding
    private fun startPreferredNetwork() { if(task.state.value.network==null) task.selectNetwork(BindingNetwork.OFF_CAMPUS) }
    override fun onSaveInstanceState(outState:Bundle) { outState.putBoolean("editingBinding",editingBinding);super.onSaveInstanceState(outState) }
    private fun updateFormState() {
        val bound=showBoundSummary()
        binding.boundSummary.visibility=if(bound) View.VISIBLE else View.GONE
        binding.bindingCard.visibility=if(bound) View.GONE else View.VISIBLE
        binding.boundAccount.text=com.mychuannong.app.common.data.local.mmkv.StudentInfoManager.greeting + " · " + task.number

        val state=task.state.value
        val selectedId=when(state.network) {
            BindingNetwork.CAMPUS -> R.id.network_campus
            BindingNetwork.OFF_CAMPUS -> R.id.network_off_campus
            null -> View.NO_ID
        }
        if(binding.networkOptions.checkedRadioButtonId!=selectedId) binding.networkOptions.check(selectedId)
        binding.networkCampus.isEnabled=!state.busy
        binding.networkOffCampus.isEnabled=!state.busy
        binding.connectWebvpn.visibility=if(state.network!=null) View.VISIBLE else View.GONE
        binding.connectWebvpn.isEnabled=!state.busy
        binding.connectWebvpn.setText(when {
            state.vpnBound -> R.string.binding_vpn_rebind
            state.network==BindingNetwork.CAMPUS -> R.string.binding_vpn_recommended
            else -> R.string.binding_vpn_action
        })
        binding.bindingStep.text=if(state.canLogin) "第二步 · 登录教务账号" else "第一步 · 连接校园 VPN"
        binding.useDirect.isEnabled=!state.busy
        binding.useDirect.text=if(state.network==BindingNetwork.CAMPUS) "改用校园 VPN（推荐）" else "已连接校园网？使用直连"
        binding.academicForm.visibility=if(state.canLogin) View.VISIBLE else View.GONE
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
            placeholderRes = if(task.state.value.network==BindingNetwork.CAMPUS && !task.state.value.vpnBound) R.string.binding_password_direct_hint else R.string.binding_password_hint
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
        val hasPassword = task.state.value.vpnBound || password.text.isNotBlank()
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

}
