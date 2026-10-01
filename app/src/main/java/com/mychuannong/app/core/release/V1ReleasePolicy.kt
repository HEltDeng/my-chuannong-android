package com.mychuannong.app.core.release

/** Non-overridable product boundaries for the personal-developer V1 release. */
object V1ReleasePolicy {
    const val CLOSED_MESSAGE = "V1 初代首发版暂不开放此能力"

    private val hardClosedFeatures = setOf(
        "sports_reservation",
        "real_payment",
        "campus_map",
    )

    fun isHardClosed(featureKey: String): Boolean = featureKey in hardClosedFeatures

    fun maySendCredentialToOwnBackend(fieldName: String): Boolean {
        val normalized = fieldName.trim().lowercase()
        return normalized !in setOf(
            "student_id",
            "student_password",
            "school_password",
            "captcha",
            "school_cookie",
            "school_token",
        )
    }
}
