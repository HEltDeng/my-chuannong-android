package com.mychuannong.app.feature.common.ui

import com.mychuannong.app.core.network.campustools.CampusContentBridge
import org.junit.Assert.*
import org.junit.Test

class CampusContentBridgeTest {
    @Test fun rejectsCredentialAdminAndExternalRoutes() {
        for (path in listOf("auth/login", "admin/users", "https://example.com/", "community/secondhand/../auth/me", "community/secondhand/%2e%2e", "community/secondhand#x")) {
            assertFalse(path, CampusContentBridge.allows("secondhand", "GET", path))
        }
        assertFalse(CampusContentBridge.allows("secondhand", "GET", "community/lost-found"))
        assertFalse(CampusContentBridge.allows("services", "POST", "services/providers"))
        assertFalse(CampusContentBridge.allows("services", "DELETE", "services/bookings/test"))
    }
    @Test fun allowsOnlyOwnContentAndIntentionWorkflow() {
        assertTrue(CampusContentBridge.allows("secondhand", "GET", "community/secondhand?mine=true&limit=100"))
        assertTrue(CampusContentBridge.allows("lost-found", "PATCH", "community/lost-found/item-1"))
        assertTrue(CampusContentBridge.allows("services", "GET", "services/providers?category=driving_school"))
        assertTrue(CampusContentBridge.allows("services", "POST", "services/bookings"))
        assertTrue(CampusContentBridge.allows("services", "PATCH", "services/bookings/test-1"))
    }
}
