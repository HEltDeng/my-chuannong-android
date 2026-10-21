package com.mychuannong.app.utils.event

data class FinishEvent(
    val name: String,
    val requestId: Long? = null
)
