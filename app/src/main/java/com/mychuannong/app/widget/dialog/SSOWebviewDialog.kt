package com.mychuannong.app.widget.dialog
import android.content.DialogInterface
import android.os.Bundle
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.mychuannong.app.SchoolWebActivity
import com.mychuannong.app.school.SicauAdapter
import okhttp3.Cookie
class SSOWebviewDialog(
 val loginResult:(String,String,String,String,List<Cookie>)->Unit,
 val onDismissCallback:(()->Unit)?=null
):BottomSheetDialogFragment() {
 override fun onCreate(state:Bundle?) { super.onCreate(state)
  startActivity(android.content.Intent(requireContext(),SchoolWebActivity::class.java).putExtra("url",SicauAdapter.ACADEMIC).putExtra("title","川农教务登录"))
  // No success callback until a school identity reader has real authenticated evidence.
  dismiss()
 }
 override fun onDismiss(dialog:DialogInterface) { super.onDismiss(dialog);onDismissCallback?.invoke() }
}
