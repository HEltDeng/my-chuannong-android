package com.mychuannong.app.core.network.campustools

import com.google.gson.annotations.SerializedName

data class PhoneAuthCapabilities(@SerializedName("availablePurposes") val availablePurposes: List<String>, @SerializedName("resetLimit") val resetLimit: Int, @SerializedName("policyVersion") val policyVersion: String)
data class SmsChallenge(@SerializedName("challengeId") val challengeId: String, @SerializedName("expiresIn") val expiresIn: Int, @SerializedName("retryAfter") val retryAfter: Int)
data class SmsCodeRequest(@SerializedName("phone") val phone: String, @SerializedName("purpose") val purpose: String, @SerializedName("policyVersion") val policyVersion: String, @SerializedName("consentAcceptedAt") val consentAcceptedAt: Long)
data class ResetPasswordRequest(@SerializedName("phone") val phone: String, @SerializedName("password") val password: String, @SerializedName("confirmPassword") val confirmPassword: String,
    @SerializedName("challengeId") val challengeId: String, @SerializedName("code") val code: String, @SerializedName("policyVersion") val policyVersion: String, @SerializedName("consentAcceptedAt") val consentAcceptedAt: Long)
data class VerifyPhoneRequest(@SerializedName("challengeId") val challengeId: String, @SerializedName("code") val code: String, @SerializedName("policyVersion") val policyVersion: String, @SerializedName("consentAcceptedAt") val consentAcceptedAt: Long)
data class PasswordResetResult(@SerializedName("reset") val reset: Boolean, @SerializedName("loginRequired") val loginRequired: Boolean, @SerializedName("remainingToday") val remainingToday: Int)

data class StudentIdentityRequest(@SerializedName("studentId") val studentId: String, @SerializedName("realName") val realName: String, @SerializedName("schoolCode") val schoolCode: String,
    @SerializedName("policyVersion") val policyVersion: String, @SerializedName("consentAcceptedAt") val consentAcceptedAt: Long)
data class StudentIdentityResult(@SerializedName("studentId") val studentId: String?, @SerializedName("realName") val realName: String?, @SerializedName("identityStatus") val identityStatus: String, @SerializedName("phoneVerified") val phoneVerified: Boolean)
