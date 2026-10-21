package com.mychuannong.app.core.security

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.TimeUnit

/** Process-local state for sessions that are deliberately never persisted by the app. */
object SchoolSessionState {
    private val readyTtlNanos = TimeUnit.MINUTES.toNanos(30)
    private val readyAtNanos = AtomicLong(0L)

    val bootstrapComplete = AtomicBoolean(false)
    val bootstrapInFlight = AtomicBoolean(false)
    val explicitBindingInFlight = AtomicBoolean(false)
    val generation = AtomicLong(0L)

    fun markReady() {
        readyAtNanos.set(System.nanoTime())
        bootstrapComplete.set(true)
    }

    fun isReady(): Boolean {
        if (!bootstrapComplete.get()) return false
        val readyAt = readyAtNanos.get()
        if (readyAt <= 0L || System.nanoTime() - readyAt >= readyTtlNanos) {
            invalidate()
            return false
        }
        return true
    }

    fun isAuthenticationFailure(message: String?): Boolean {
        val normalized = message?.trim().orEmpty()
        if (normalized.isEmpty()) return false
        return normalized.contains("403") ||
            normalized.contains("未登录") ||
            normalized.contains("请先登录") ||
            normalized.contains("登录已失效") ||
            normalized.contains("登录状态失效") ||
            normalized.contains("会话已失效") ||
            normalized.contains("unauthorized", ignoreCase = true) ||
            normalized.contains("session expired", ignoreCase = true)
    }

    fun invalidate() {
        bootstrapComplete.set(false)
        readyAtNanos.set(0L)
        generation.incrementAndGet()
    }
}
