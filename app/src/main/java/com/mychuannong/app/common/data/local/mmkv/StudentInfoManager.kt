package com.mychuannong.app.common.data.local.mmkv
import com.tencent.mmkv.MMKV
/** Local verified identity, entirely separate from platform login. No school password is stored. */
object StudentInfoManager {
 private val kv by lazy { MMKV.mmkvWithID("sicau_verified_identity") }
 private var cachedId:String?=null
 val studentId:String get() {
  cachedId?.let{return it}
  val legacy=kv.decodeString("id","").orEmpty()
  if(legacy.isNotBlank()) { com.mychuannong.app.school.SchoolSessionVault.saveIdentity(legacy);kv.removeValueForKey("id") }
  return runCatching { com.mychuannong.app.school.SchoolSessionVault.readIdentity() }.getOrElse { kv.encode("verified",false);"" }.also{cachedId=it}
 }
 // Compatibility marker for retained upper-layer guards; never a password or authentication input.
 val studentPassword:String get()=if(hasCompleteBinding) "local-web-session" else ""
 // Old unified-auth-only bindings require a jiaowu profile verification once after upgrading.
 val hasCompleteBinding:Boolean get()=studentId.isNotBlank() && kv.decodeBool("jiaowu_verified_v1",false)
 private val profiles get()=com.mychuannong.app.school.LearningCacheKeys.store("verified_school_profiles")
 val studentName:String get()=if(hasCompleteBinding) profiles.decodeString(com.mychuannong.app.school.LearningCacheKeys.owner(studentId),"").orEmpty() else ""
 val greeting:String get()=com.mychuannong.app.school.StudentGreeting.from(studentName,hasCompleteBinding)
 internal fun saveVerifiedName(id:String,name:String?) {
  if(name.isNullOrBlank()) return
  profiles.encode(com.mychuannong.app.school.LearningCacheKeys.owner(id),name)
 }
 internal class Credentials(val studentId:String,val studentPassword:String)
 internal fun credentialsOrNull():Credentials?=if(hasCompleteBinding) Credentials(studentId,studentPassword) else null
 internal fun acceptVerifiedIdentity(id:String) { require(id.isNotBlank());val changed=studentId!=id;if(changed) kv.removeValueForKey("admission_year");com.mychuannong.app.school.SchoolSessionVault.saveIdentity(id);cachedId=id;kv.encode("jiaowu_verified_v1",true);if(changed)academicRevision.value++ }
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
 fun clear() { com.mychuannong.app.school.SchoolSessionVault.clearIdentity();cachedId="";kv.clearAll();profiles.clearAll();academicRevision.value++ }
 // Expiry concerns the online session, not the already verified owner of local caches.
 internal fun expire() { kv.encode("verified",false) }
}
