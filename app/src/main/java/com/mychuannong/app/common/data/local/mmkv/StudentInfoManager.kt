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
 private val years by lazy { MMKV.mmkvWithID("sicau_account_academic_preferences") }
 private fun yearKey(id:String)="year_"+java.security.MessageDigest.getInstance("SHA-256").digest(id.toByteArray()).joinToString(""){"%02x".format(it)}
 val academicRevision=kotlinx.coroutines.flow.MutableStateFlow(0)
 val admissionYear:Int? get() {
  if(studentId.isBlank())return null
  val key=yearKey(studentId)
  if(!years.containsKey(key) && kv.containsKey("admission_year")) { years.encode(key,kv.decodeInt("admission_year",0));kv.removeValueForKey("admission_year") }
  return years.decodeInt(key,0).takeIf{it in 1900..2200}
 }
 internal fun setAdmissionYearForVerifiedAccount(id:String,year:Int?) {
  require(hasCompleteBinding && studentId==id)
  if(year==null) years.removeValueForKey(yearKey(id)) else { require(year in 1900..2200);years.encode(yearKey(id),year) };academicRevision.value++
 }
 fun clear() { kv.clearAll() }
 internal fun expire() { kv.encode("verified",false) }
}
