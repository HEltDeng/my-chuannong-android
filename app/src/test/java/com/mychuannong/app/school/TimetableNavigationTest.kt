package com.mychuannong.app.school
import org.junit.Assert.*
import org.junit.Test
class TimetableNavigationTest {
 @Test fun acceptsDistinctSchoolSuppliedQueriesWithoutFixedStudentOrTerm() {
  val root="https://jiaowu.sicau.edu.cn/xuesheng/gongxuan/gongxuan/kbbanji.asp"
  assertTrue(TimetableNavigation.allowed("$root?title_id1=27"))
  assertTrue(TimetableNavigation.allowed("$root?title_id1=93&term=2027-2028-2"))
  assertTrue(TimetableNavigation.allowed(root))
  assertFalse(TimetableNavigation.allowed("https://untrusted.example/xuesheng/gongxuan/gongxuan/kbbanji.asp?title_id1=27"))
  assertFalse(TimetableNavigation.allowed("https://user@jiaowu.sicau.edu.cn/xuesheng/gongxuan/gongxuan/kbbanji.asp"))
  assertFalse(TimetableNavigation.allowed(root.replace("kbbanji.asp","del.asp")))
  assertFalse(TimetableNavigation.allowed(root.replace("https:","javascript:")))
 }
}
