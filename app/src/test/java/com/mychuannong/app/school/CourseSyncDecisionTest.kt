package com.mychuannong.app.school

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CourseSyncDecisionTest {
    @Test fun missingCurrentTermIsFetchedEvenWhenOlderTermsWerePreviouslySaved() {
        assertTrue(shouldFetchCourses(refreshExisting = false, hasCurrentTermCourses = false, currentTermUpdatedAt = 0))
        assertFalse(shouldFetchCourses(refreshExisting = false, hasCurrentTermCourses = true, currentTermUpdatedAt = 0))
        assertFalse(shouldFetchCourses(refreshExisting = false, hasCurrentTermCourses = false, currentTermUpdatedAt = 123))
        assertTrue(shouldFetchCourses(refreshExisting = true, hasCurrentTermCourses = true, currentTermUpdatedAt = 123))
    }
}
