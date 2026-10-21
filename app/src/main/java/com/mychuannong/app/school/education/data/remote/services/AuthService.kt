package com.mychuannong.app.school.education.data.remote.services
/** No native password form or guessed school login request is supported. */
object AuthService {
 suspend fun login(username:String,password:String):Boolean = false
 suspend fun CheckLoginStates():Boolean = false
}
