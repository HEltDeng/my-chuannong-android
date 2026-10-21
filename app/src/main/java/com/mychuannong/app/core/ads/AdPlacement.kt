package com.mychuannong.app.core.ads

/**
 * Stable advertising placement identifiers.
 *
 * Keep these identifiers independent from any advertising SDK so a provider can be
 * introduced or replaced without changing feature code.
 */
enum class AdPlacement(val stableId: String) {
    HOME_BANNER("home_banner"),
    COMMUNITY_FEED_INLINE("community_feed_inline"),
    RESOURCE_LIBRARY_INLINE("resource_library_inline")
}
