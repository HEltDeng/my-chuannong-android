package com.mychuannong.app.common.data.local.mmkv
import com.tencent.mmkv.MMKV
/** Local verified identity, entirely separate from platform login. No school password is stored. */
object StudentInfoManager {
 private val kv by lazy { MMKV.mmkvWithID("sicau_verified_identity") }
 val studentId:String get()=kv.decodeString("id","").orEmpty()
 // Compatibility marker for retained upper-layer guards; never a password or authentication input.
 val studentPassword:String get()=if(hasCompleteBinding) "local-web-session" else ""
 val hasCompleteBinding:Boolean get()=studentId.isNotBlank() && kv.decodeBool("verified",false)
 internal class Credentials(val studentId:String,val studentPassword:String)
 internal fun credentialsOrNull():Credentials?=if(hasCompleteBinding) Credentials(studentId,studentPassword) else null
 internal fun acceptVerifiedIdentity(id:String) { require(id.isNotBlank());if(studentId!=id) kv.removeValueForKey("admission_year");kv.encode("id",id);kv.encode("verified",true) }
 fun saveCredentials(studentId:String,studentPassword:String):Boolean=false
 val admissionYear:Int? get() = kv.decodeInt("admission_year",0).takeIf { it in 1900..2200 && studentId.isNotBlank() }
 internal fun setAdmissionYearForVerifiedAccount(id:String,year:Int?) {
  require(hasCompleteBinding && studentId==id)
  if(year==null) kv.removeValueForKey("admission_year") else { require(year in 1900..2200);kv.encode("admission_year",year) }
 }
 fun clear() { kv.clearAll() }
 internal fun expire() { kv.encode("verified",false) }
}
