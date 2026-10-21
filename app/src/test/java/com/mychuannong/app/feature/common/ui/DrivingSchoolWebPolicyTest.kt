package com.mychuannong.app.feature.common.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DrivingSchoolWebPolicyTest {
    @Test fun localPageAllowsSchoolAndDialogHashNavigation() {
        for (hash in listOf("", "#/list", "#/a", "#/b", "#/c", "#/a/book", "#/c/location")) {
            assertTrue(DrivingSchoolWebPolicy.allowsNavigation(DrivingSchoolWebPolicy.HOME_URL + hash))
        }
    }

    @Test fun onlyDrivingSchoolAssetsCanLoad() {
        val root = "https://appassets.androidplatform.net/assets/driving-school/"
        for (asset in listOf("index.html", "online.css", "online.js")) {
            assertTrue(DrivingSchoolWebPolicy.allowsAssetRequest(root + asset))
        }
        assertTrue(DrivingSchoolWebPolicy.allowsAssetRequest(DrivingSchoolWebPolicy.HOME_URL))
        for (url in listOf(
            "https://example.com/assets/driving-school/app.js",
            "http://appassets.androidplatform.net/assets/driving-school/app.js",
            "https://appassets.androidplatform.net:443/assets/driving-school/app.js",
            "https://user@appassets.androidplatform.net/assets/driving-school/app.js",
            root + "data.js", root + "app.js", root + "styles.css", root + "../app-config/default-v1.json",
            root + "%2e%2e/app-config/default-v1.json",
            root + "assets/%73chool-a.png",
            root + "app.js?redirect=https://example.com",
            "file:///data/user/0/private.txt", "content://private", "javascript:alert(1)",
        )) assertFalse(url, DrivingSchoolWebPolicy.allowsAssetRequest(url))
    }

    @Test fun externalAndOtherAssetDocumentsCannotReplaceThePage() {
        for (url in listOf(
            "https://example.com", "tel:10086", "https://appassets.androidplatform.net/assets/secondhand/index.html",
            DrivingSchoolWebPolicy.HOME_URL + "&redirect=https://example.com", "javascript:alert(1)",
        )) assertFalse(url, DrivingSchoolWebPolicy.allowsNavigation(url))
    }
}
