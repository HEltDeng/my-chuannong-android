package com.mychuannong.app.school

import org.junit.Assert.*
import org.junit.Test

class TimetableImportRulesTest {
    @Test fun parityAndDisjointRangesAreActualTeachingWeeks() {
        assertEquals(listOf(1,3,5,7,9,11,13,15),TimetableImportRules.weeks("1-16周（单周）"))
        assertEquals(listOf(2,4,8,10),TimetableImportRules.weeks("1-4,7-10(双周)"))
        assertEquals(listOf(3,5,7),TimetableImportRules.weeks("3、5、7"))
    }
    @Test fun missingHyphenAmbiguousParityAndOutOfBoundsAreRejected() {
        listOf("114周","1-16(单双周)","16-1","0-8","1-31","2(单周)","1-16待确认").forEach {
            assertTrue(it,runCatching { TimetableImportRules.weeks(it) }.isFailure)
        }
        assertFalse(TimetableImportRules.term("2026-2028-1"))
    }
    @Test fun multipleCoursesInOneCellStaySeparateAndUnreviewed() {
        val drafts=TimetableImportRules.cell("测试课程甲:教师甲\n雅安校区:测试教室\n1-8周(单周)(2学时)\n测试课程乙:教师乙\n雅安校区:另一教室\n9-16周(双周)(2学时)",3,5)
        assertEquals(2,drafts.size)
        assertEquals(listOf(1,3,5,7),drafts[0].course("2026-2027-1","synthetic").weeks)
        assertEquals(listOf(10,12,14,16),drafts[1].course("2026-2027-1","synthetic").weeks)
        assertTrue(drafts.none { it.reviewed })
    }
    @Test fun roomUniqueKeyDoesNotLoseDistinctWeekSets() {
        val a=TimetableImportRules.Draft("测试课程","测试教师","测试室","2","3","4","1-8(单周)","").course("2026-2027-1","synthetic")
        val b=a.copy(weeks=listOf(10,12))
        val merged=TimetableImportRules.merge(listOf(a,b,a))
        assertEquals(1,merged.size)
        assertEquals(listOf(1,3,5,7,10,12),merged.single().weeks)
        assertEquals(2,TimetableImportRules.merge(listOf(a,b.copy(classroom="另一室"))).size)
    }
    @Test fun corruptRangeNeverBecomesItsLastDigitOrEveryWeek() {
        listOf("1一4周(2学时)","7H4周(连堂4学时)","1-16周(周)(2学时)").forEach { token ->
            assertEquals("",TimetableImportRules.cell("合成课程:合成教师\n$token",1,1).single().weeks)
        }
    }
}
