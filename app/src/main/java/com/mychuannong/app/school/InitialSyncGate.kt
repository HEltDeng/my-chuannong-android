package com.mychuannong.app.school

import kotlinx.coroutines.Job

/** An explicit refresh replaces a pending initial background sync instead of queueing behind it. */
internal class InitialSyncGate {
    private var active: Job? = null

    @Synchronized fun register(job: Job): Boolean {
        if (active?.isActive == true) return false
        active = job
        return true
    }
    @Synchronized fun finish(job: Job) { if (active === job) active = null }
    fun cancelForUserAction() {
        val prior = synchronized(this) { active.also { active = null } }
        prior?.cancel()
    }
}
