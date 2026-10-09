package com.mychuannong.app.settings.ui

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
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
    companion object { const val EXTRA_IS_SWITCH_ACCOUNT="extra_is_switch_account" }
    private val task:NativeBindingViewModel by viewModels()
    private val username:TextView get()=binding.etStudentId
    private val password:TextView get()=binding.etStudentPassword
    private val save get()=binding.saveUser
    private var openingTimetable=false
    private var captchaDialog:android.app.AlertDialog?=null
    private lateinit var status:TextView
    override fun createViewBinding()=ActivityBindingUserBinding.inflate(layoutInflater)
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        // School credentials remain in this form's memory; do not offer them to system autofill.
        binding.bindingUserLayout.importantForAutofill=View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        username.isSaveEnabled=false
        password.isSaveEnabled=false
        binding.etAcademicPassword.isSaveEnabled=false
        binding.etAcademicPassword.setText(task.academicPassword)
        binding.etAcademicPassword.doAfterTextChanged { task.academicPassword=it.toString() }
        ViewCompat.setOnApplyWindowInsetsListener(binding.toolbar) { view,insets ->
            val bars=insets.getInsets(WindowInsetsCompat.Type.systemBars());view.setPadding(view.paddingLeft,bars.top+(10*resources.displayMetrics.density).toInt(),view.paddingRight,view.paddingBottom);insets
        }
        username.text=task.number;password.text=task.password
        username.doAfterTextChanged { task.number=it.toString();binding.studentIdInputLayout.error=null;updateInputHints();updateFormState() }
        password.doAfterTextChanged { task.password=it.toString();binding.passwordInputLayout.error=null;updateInputHints();updateFormState() }
        username.setOnFocusChangeListener { _,focus -> updateInputHints();if(!focus && username.text.isBlank()) binding.studentIdInputLayout.error=getString(R.string.binding_student_id_required) }
        password.setOnFocusChangeListener { _,focus -> updateInputHints();if(!focus && password.text.isBlank()) binding.passwordInputLayout.error=getString(R.string.binding_password_required) }
        username.setOnEditorActionListener { _,action,_ -> if(action==EditorInfo.IME_ACTION_NEXT){password.requestFocus();true}else false }
        password.setOnEditorActionListener { _,action,_ -> if(action==EditorInfo.IME_ACTION_DONE){submit();true}else false }
        save.setOnClickListener { submit() }
        binding.bindingBack.setOnClickListener { task.cancel();finish() }
        binding.bindingPrivacyPolicyLink.setOnClickListener { Route.goReleaseInformation(this,ReleaseDocumentType.PRIVACY) }
        binding.webLoginBtn.text="解绑学校账号与清除会话"
        binding.webLoginBtn.setOnClickListener {
            if(task.state.value.phase in setOf(BindingPhase.LOADING,BindingPhase.VERIFICATION)) task.cancel()
            else android.app.AlertDialog.Builder(this).setTitle("清除学校会话？").setMessage("清除本应用学校会话及学习缓存；平台账号保持登录。")
                .setPositiveButton("清除") { _,_->task.cancel();AccountDataManager.clearSchoolBinding();SicauAdapter.clear();status.text="学校账号已解绑" }.setNegativeButton("取消",null).show()
        }
        status=TextView(this).apply { textSize=14f;setPadding(0,16,0,8) };binding.bindingUserLayout.addView(status)
        updateInputHints();updateFormState()
        lifecycleScope.launch { task.state.collect { state ->
            val loading=state.phase==BindingPhase.LOADING
            val verifying=state.phase==BindingPhase.VERIFICATION
            binding.loadingLayout.visibility=if(loading) View.VISIBLE else View.GONE
            binding.bindingUserLayout.visibility=if(loading || verifying) View.GONE else View.VISIBLE
            binding.webLoginBtn.text=if(loading || state.phase==BindingPhase.VERIFICATION) "取消当前认证" else "解绑学校账号与清除会话"
            binding.webLoginBtn.visibility=if(loading || state.phase==BindingPhase.VERIFICATION || com.mychuannong.app.common.data.local.mmkv.StudentInfoManager.studentId.isNotBlank()) View.VISIBLE else View.GONE
            status.text=state.message;status.visibility=if(state.message.isBlank()) View.GONE else View.VISIBLE;updateFormState()
            if(state.phase==BindingPhase.CONFIRMED && !openingTimetable) {
                openingTimetable=true
                password.text="";task.password="";binding.etAcademicPassword.setText("");task.academicPassword=""
                android.widget.Toast.makeText(this@BindingUserActivity,state.message,android.widget.Toast.LENGTH_LONG).show()
                // Binding ends at the verified native jiaowu session; course adaptation is separate.
                setResult(RESULT_OK);finish()
            }
            if(state.phase==BindingPhase.VERIFICATION && captchaDialog==null) showCaptcha()
            if(state.phase!=BindingPhase.VERIFICATION) { captchaDialog?.dismiss();captchaDialog=null }
        } }
    }
    private fun submit() {
        if(!validateForm(true)) return
        getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(password.windowToken,0)
        task.begin()
    }
    private fun updateFormState() {
        save.isEnabled=username.text.isNotBlank() && password.text.isNotBlank() && task.state.value.phase !in setOf(BindingPhase.LOADING,BindingPhase.VERIFICATION)
    }
    private fun showCaptcha() {
        val challenge=task.challenge ?: return
        val bytes=runCatching { android.util.Base64.decode(challenge.image.substringAfter(','),android.util.Base64.DEFAULT) }.getOrNull() ?: return
        val bitmap=android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.size) ?: return
        val image=ImageView(this).apply { setImageBitmap(bitmap);adjustViewBounds=true;minimumHeight=150;contentDescription="学校图形验证码" }
        val input=EditText(this).apply { hint="请输入学校图形验证码";isSingleLine=true;isSaveEnabled=false;importantForAutofill=View.IMPORTANT_FOR_AUTOFILL_NO }
        val layout=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setPadding(40,16,40,16);addView(image);addView(input) }
        val dialog=android.app.AlertDialog.Builder(this).setTitle("完成学校验证")
            .setView(layout).setPositiveButton("继续",null).setNeutralButton("刷新验证码") { _,_->task.fetchChallenge() }
            .setNegativeButton("取消") { _,_->task.cancel() }.create()
        captchaDialog=dialog;dialog.setOnCancelListener { task.cancel() }
        dialog.show()
        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if(input.text.isNullOrBlank()) input.error="请输入验证码" else { val code=input.text.toString();input.text.clear();task.submitVerification(code) }
        }
    }
    override fun onDestroy() { captchaDialog?.dismiss();captchaDialog=null;if(isFinishing) task.cancel();super.onDestroy() }
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
            placeholderRes = R.string.binding_password_hint
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

}
