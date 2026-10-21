package com.mychuannong.app.feature.lostfound.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LostFoundWebPolicyTest {
    @Test fun bundledAssetsAndEncodedEntryParametersAreAllowed() {
        assertTrue(LostFoundWebPolicy.allowsAsset(LostFoundWebPolicy.HOME))
        assertTrue(LostFoundWebPolicy.allowsAsset("https://appassets.androidplatform.net/assets/lost-found/assets/index-a.js"))
        assertTrue(LostFoundWebPolicy.allowsDocument(LostFoundWebPolicy.HOME + "?post=mine-a%2Fb#detail"))
        assertTrue(LostFoundWebPolicy.isExit(LostFoundWebPolicy.EXIT))
    }
    @Test fun externalContentOtherFeaturesAndTraversalAreBlocked() {
        listOf("https://example.com/assets/lost-found/index.html", "http://appassets.androidplatform.net/assets/lost-found/index.html",
            "https://appassets.androidplatform.net:443/assets/lost-found/index.html", "https://user@appassets.androidplatform.net/assets/lost-found/index.html",
            "https://appassets.androidplatform.net/assets/secondhand/index.html", "file:///data/data/private", "javascript:alert(1)",
            "https://appassets.androidplatform.net/assets/lost-found/../secondhand/index.html", "https://appassets.androidplatform.net/assets/lost-found/%2e%2e/secret",
            "https://appassets.androidplatform.net/assets/lost-found//secret").forEach { assertFalse(it, LostFoundWebPolicy.allowsAsset(it)) }
    }
    @Test fun onlyEntryDocumentAndExactExitCanNavigate() {
        assertFalse(LostFoundWebPolicy.allowsDocument("https://appassets.androidplatform.net/assets/lost-found/assets/photo.png"))
        assertFalse(LostFoundWebPolicy.isExit(LostFoundWebPolicy.EXIT + "?unexpected=1"))
        assertFalse(LostFoundWebPolicy.allowsDocument("not a URL"))
    }
}
