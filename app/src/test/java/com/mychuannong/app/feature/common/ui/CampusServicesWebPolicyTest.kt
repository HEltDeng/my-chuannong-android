package com.mychuannong.app.feature.common.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CampusServicesWebPolicyTest {
    @Test fun entryRoutesOpenTheirOwnListsAndRejectUnknownModules() {
        for (service in CampusService.entries) {
            assertEquals(service, CampusService.fromRoute(service.route))
            assertEquals(CampusServicesWebPolicy.DOCUMENT_URL + "#/" + service.route,
                CampusServicesWebPolicy.homeUrl(service))
            for (suffix in listOf("", "/a", "/b", "/c", "/c/book/general", "/a/records")) {
                assertTrue(CampusServicesWebPolicy.allowsNavigation(CampusServicesWebPolicy.homeUrl(service) + suffix))
            }
        }
        for (value in listOf(null, "", "home", "../gym", "https://example.com")) assertNull(CampusService.fromRoute(value))
    }

    @Test fun onlyTheManagedAssetsLoad() {
        val root = "https://appassets.androidplatform.net/assets/campus-services/"
        for (file in listOf("index.html", "online.css", "online.js")) {
            assertTrue(CampusServicesWebPolicy.allowsAssetRequest(root + file))
        }
        assertTrue(CampusServicesWebPolicy.allowsAssetRequest(CampusServicesWebPolicy.DOCUMENT_URL))
        for (url in listOf("https://example.com/assets/campus-services/app.js",
            root.replace("https:", "http:") + "app.js",
            root.replace(".net/", ".net:443/") + "app.js",
            root.replace("https://", "https://user@") + "app.js",
            root + "data.js", root + "app.js", root + "styles.css", root + "../app-config/default-v1.json", root + "%2e%2e/secondhand/app.js",
            root + "%61pp.js", root + "app.js?host=android", root + "missing.png",
            "file:///data/user/0/private.txt", "content://private", "javascript:alert(1)")) {
            assertFalse(url, CampusServicesWebPolicy.allowsAssetRequest(url))
        }
    }

    @Test fun navigationCannotEscapeToAnotherDocumentOrProtocol() {
        for (url in listOf("https://example.com", "tel:10086", "javascript:alert(1)",
            CampusServicesWebPolicy.DOCUMENT_URL + "&redirect=https://example.com",
            "https://appassets.androidplatform.net/assets/secondhand/index.html",
            "https://appassets.androidplatform.net/assets/campus-services/app.js")) {
            assertFalse(url, CampusServicesWebPolicy.allowsNavigation(url))
        }
    }
}
