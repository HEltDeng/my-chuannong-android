package com.mychuannong.app.settings.ui
import android.os.Bundle
import android.content.Intent
import android.view.View
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.mychuannong.app.base.FullScreenActivity
import com.mychuannong.app.databinding.ActivityBindingUserBinding
import com.mychuannong.app.school.SicauAdapter
import com.mychuannong.app.SchoolWebActivity
import com.mychuannong.app.core.privacy.AccountDataManager
import kotlinx.coroutines.launch
class BindingUserActivity:FullScreenActivity<ActivityBindingUserBinding>() {
 companion object { const val EXTRA_IS_SWITCH_ACCOUNT="extra_is_switch_account" }
 override fun createViewBinding()=ActivityBindingUserBinding.inflate(layoutInflater)
 override fun onCreate(state:Bundle?) {
  super.onCreate(state)
  binding.bindingBack.setOnClickListener { finish() }
  binding.studentIdInputLayout.visibility=View.GONE
  binding.passwordInputLayout.visibility=View.GONE
  binding.loadingLayout.visibility=View.GONE
  val status=TextView(this).apply { textSize=15f;setPadding(0,24,0,24) }
  binding.bindingUserLayout.addView(status)
  lifecycleScope.launch { SicauAdapter.status.collect { status.text=it.message } }
  binding.webLoginBtn.text="在学校页面登录"
  binding.webLoginBtn.setOnClickListener {
   SicauAdapter.opening()
   startActivity(Intent(this,SchoolWebActivity::class.java).putExtra("url",SicauAdapter.ACADEMIC).putExtra("title","川农教务 · 本机学校会话"))
  }
  binding.saveUser.text="尝试同步本人数据"
  binding.saveUser.isEnabled=true
  binding.saveUser.setOnClickListener { lifecycleScope.launch { com.mychuannong.app.school.SchoolSyncEngine.sync() } }
  binding.bindingPrivacyPolicyLink.text="解绑并清除本机学校会话"
  binding.bindingPrivacyPolicyLink.setOnClickListener {
   android.app.AlertDialog.Builder(this).setTitle("清除学校会话？").setMessage("清除本应用的学校网页会话及学习缓存；我的川农平台账号保持登录。")
    .setPositiveButton("清除") { _,_->lifecycleScope.launch { com.mychuannong.app.school.SchoolSyncEngine.clear();AccountDataManager.clearSchoolBinding();SicauAdapter.clear() } }.setNegativeButton("取消",null).show()
  }
 }
}
