package com.mychuannong.app.core.community

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CommunityBlockRulesTest {
    @Test fun blocksOneAuthorAndUnblocksOnlyThatAuthor() {
        val first = CommunityBlockRules.add(emptyList(), "viewer", "author-a", " 用户甲 ")
        val both = CommunityBlockRules.add(first, "viewer", "author-b", "")
        assertEquals(listOf(BlockedCommunityUser("author-a", "用户甲"), BlockedCommunityUser("author-b", "校园用户")), both)
        assertEquals(both, CommunityBlockRules.add(both, "viewer", "author-a", "其他名称"))
        assertEquals(listOf(BlockedCommunityUser("author-b", "校园用户")), CommunityBlockRules.remove(both, "author-a"))
    }

    @Test fun cannotBlockOnBehalfOfAnonymousOrBlockYourself() {
        assertThrows(IllegalArgumentException::class.java) { CommunityBlockRules.add(emptyList(), "", "other", "") }
        assertThrows(IllegalArgumentException::class.java) { CommunityBlockRules.add(emptyList(), "viewer", "viewer", "") }
        assertThrows(IllegalArgumentException::class.java) { CommunityBlockRules.add(emptyList(), "viewer", "bad\nidentity", "") }
    }
}
