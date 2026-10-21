package com.mychuannong.app.core.security

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal enum class SchoolAccess { BINDING_REQUIRED, ALLOWED }
internal data class SchoolAccessStatus(val access: SchoolAccess)

/** App access follows a verified binding, not network reachability. The persisted proof
 * must be authenticated by device encryption; preferences and UI result codes cannot grant it. */
internal class SchoolAccessController(
    private val currentIdentity: () -> String,
    private val trustedBindingOwner: () -> String?,
    private val persistRevocation: () -> Unit,
) {
    private val mutable = MutableStateFlow(SchoolAccessStatus(SchoolAccess.BINDING_REQUIRED))
    val state = mutable.asStateFlow()
    private var initialized = false
    private var verifiedOwner: String? = null

    @Synchronized fun isAllowed(): Boolean {
        if (!initialized) restoreBinding()
        return mutable.value.access == SchoolAccess.ALLOWED &&
            verifiedOwner?.let { it.isNotBlank() && it == currentIdentity() } == true
    }

    /** Local-only, including after a successful binding. Never sends a second login request. */
    @Synchronized fun restoreBinding(): SchoolAccessStatus {
        initialized = true
        val owner = currentIdentity()
        verifiedOwner = runCatching { trustedBindingOwner() }.getOrNull()
            ?.takeIf { it.isNotBlank() && it == owner }
        mutable.value = SchoolAccessStatus(if (verifiedOwner != null) SchoolAccess.ALLOWED else SchoolAccess.BINDING_REQUIRED)
        return mutable.value
    }

    /** Only explicit unbinding revokes first-use completion. Online expiry is separate. */
    @Synchronized fun revokeBinding() {
        initialized = true
        verifiedOwner = null
        mutable.value = SchoolAccessStatus(SchoolAccess.BINDING_REQUIRED)
        // Persist explicit unbinding so restarting cannot restore the removed account.
        persistRevocation()
    }
}
