package com.mychuannong.app.core.analytics

/** Successful sends are throttled per account, but a new business day sends immediately. */
internal class ForegroundActivityPolicy {
    private var lastAccount: String? = null
    private var lastDay: String? = null
    private var lastSent: Long = 0

    fun shouldSend(account: String?, day: String, elapsed: Long, consent: Boolean, foreground: Boolean): Boolean =
        consent && foreground && !account.isNullOrBlank() &&
            (account != lastAccount || day != lastDay || elapsed - lastSent >= 300_000L)

    fun accepted(account: String, day: String, elapsed: Long) {
        lastAccount = account
        lastDay = day
        lastSent = elapsed
    }
}
