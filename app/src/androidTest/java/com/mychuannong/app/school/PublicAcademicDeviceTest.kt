package com.mychuannong.app.school
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*
import com.google.gson.Gson

/** Public-source fixture only, never overwrites production cache or uses an account. */
@RunWith(AndroidJUnit4::class)
class PublicAcademicDeviceTest {
    @Test fun verifiedSeedAndOfflineSchedule() {
        val d=SicauAcademicData.current()
        assertEquals(2,d.terms.size);assertEquals(10,SicauAcademicData.periods().size)
        assertEquals("2026-09-07",SicauAcademicData.term("2026-2027-1")!!.semesterStart)
        assertEquals("20:25",SicauAcademicData.periods().last().start.toString())
        assertEquals("2027-03-01",d.terms.last().semesterStart)
    }
    @Test fun sourceDiscoveryAndOnDeviceOcrValidation() {
        val assets=InstrumentationRegistry.getInstrumentation().context.assets
        val active=SicauAcademicData.current()
        val html=assets.open("official-source.html").bufferedReader().use{it.readText()}
        val discovery=SicauOfficialSource.discover(html){url->assets.open(if(url.contains("9D011"))"official-calendar.png" else "official-periods.png").use{it.readBytes()}}
        assertEquals(active.contentFingerprint,discovery.fingerprint)
        val parsed=SicauImageParser.parse(discovery,active){android.util.Log.i("PublicOcrTest",it)}
        SicauAcademicData.parse(Gson().toJson(parsed))
        assertEquals(active.periods,parsed.periods)
        assertEquals(active.terms.map{it.semesterStart},parsed.terms.map{it.semesterStart})
        assertTrue(parsed.terms.first().events.any{it.type=="practice" && it.start=="2027-01-18"})
    }

    @Test fun changedSourceParsedValidatedCachedAndReadWithoutAProductBackend() {
        val assets=InstrumentationRegistry.getInstrumentation().context.assets
        val active=SicauAcademicData.current()
        val html=assets.open("official-source.html").bufferedReader().use{it.readText()}
        val changed=SicauOfficialSource.discover(html){url->
            val bytes=assets.open(if(url.contains("9D011"))"official-calendar.png" else "official-periods.png").use{it.readBytes()}
            val bitmap=android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.size)
            val output=java.io.ByteArrayOutputStream()
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,output);bitmap.recycle();output.toByteArray()
        }
        assertNotEquals(active.contentFingerprint,changed.fingerprint)
        val candidate=SicauImageParser.parse(changed,active)
        val cache=VerifiedAcademicCache("isolated_public_academic_update_test")
        cache.commit(candidate)
        val loaded=cache.load(active)
        assertEquals(candidate.version,loaded.version)
        assertEquals("2026-09-07",loaded.terms.first().semesterStart)
        assertEquals(4,com.mychuannong.app.feature.schedule.CourseScheduleEngine.teachingWeek(java.time.LocalDate.parse(loaded.terms.first().semesterStart),java.time.LocalDate.parse("2026-10-03")))
        try {cache.commit(candidate.copy(validationStatus="pending_review"));fail("Unverified data accepted")}catch(_:IllegalArgumentException){}
        assertEquals(candidate.version,cache.load(active).version)
        assertEquals(active.version,SicauAcademicData.current().version)
    }

    @Test fun actualChangedPeriodIsParsedAndReadFromIsolatedCache() {
        val assets=InstrumentationRegistry.getInstrumentation().context.assets
        val active=SicauAcademicData.current()
        val html=assets.open("official-source.html").bufferedReader().use{it.readText()}
        val original=assets.open("official-periods.png").use{it.readBytes()}
        val source=android.graphics.BitmapFactory.decodeByteArray(original,0,original.size)
        val large=android.graphics.Bitmap.createScaledBitmap(source,source.width*3,source.height*3,false).copy(android.graphics.Bitmap.Config.ARGB_8888,true)
        val recognizer=com.google.mlkit.vision.text.TextRecognition.getClient(com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions.Builder().build())
        val result=com.google.android.gms.tasks.Tasks.await(recognizer.process(com.google.mlkit.vision.common.InputImage.fromBitmap(large,0)))
        val line=result.textBlocks.flatMap{it.lines}.single{it.text.contains("第十节")}
        val rect=line.boundingBox!!
        rect.right=(large.width*.935).toInt()
        val canvas=android.graphics.Canvas(large)
        val paint=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        paint.color=android.graphics.Color.WHITE;canvas.drawRect(rect,paint)
        paint.color=android.graphics.Color.BLACK;paint.typeface=android.graphics.Typeface.create("sans-serif",android.graphics.Typeface.BOLD);paint.textSize=rect.height()*.9f
        val replacement="第十节 20:25-21:15"
        while(paint.measureText(replacement)>rect.width())paint.textSize-=.5f
        canvas.drawText(replacement,rect.left.toFloat(),rect.centerY()-(paint.ascent()+paint.descent())/2,paint)
        val out=java.io.ByteArrayOutputStream();large.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out)
        recognizer.close();large.recycle();source.recycle()
        val found=SicauOfficialSource.discover(html){url->if(url.contains("9D011"))assets.open("official-calendar.png").use{it.readBytes()} else out.toByteArray()}
        val candidate=SicauImageParser.parse(found,active){android.util.Log.i("PublicOcrTest",it)}
        assertEquals("21:15",candidate.periods.last().end)
        val cache=VerifiedAcademicCache("isolated_semantic_update_test");cache.commit(candidate)
        assertEquals("21:15",cache.load(active).periods.last().end)
        assertEquals("21:10",SicauAcademicData.current().periods.last().end)
    }

    @Test fun officialSourceCanBeCheckedDirectlyWithoutProductBackend() {
        assertTrue(com.mychuannong.app.BuildConfig.SICAU_ACADEMIC_URL.isBlank())
        val result=SicauOfficialSource.check(SicauAcademicData.current())
        assertEquals("2026-2027",result.academicYear)
        assertEquals(2,result.terms.size)
        assertTrue(result.checkedAt.isNotBlank())
    }

    @Test fun enrollmentYearIsAccountScopedAndUsesSelectedAcademicYear() {
        val manager=com.mychuannong.app.common.data.local.mmkv.StudentInfoManager
        try {
            manager.acceptVerifiedIdentity("ISOLATED_TEST_2025")
            manager.setAdmissionYearForVerifiedAccount("ISOLATED_TEST_2025",2025)
            assertEquals("大二上",com.mychuannong.app.feature.timetable.ui.AcademicTermDisplayFormatter.format("2026-2027-1",manager.admissionYear))
            assertEquals("大二下",com.mychuannong.app.feature.timetable.ui.AcademicTermDisplayFormatter.format("2026-2027-2",manager.admissionYear))
            manager.acceptVerifiedIdentity("ISOLATED_TEST_UNKNOWN")
            assertNull(manager.admissionYear)
            assertEquals("2026秋",com.mychuannong.app.feature.timetable.ui.AcademicTermDisplayFormatter.format("2026-2027-1",manager.admissionYear))
            manager.acceptVerifiedIdentity("ISOLATED_TEST_2025")
            assertEquals(2025,manager.admissionYear)
            val instrumentation=InstrumentationRegistry.getInstrumentation()
            com.tencent.mmkv.MMKV.defaultMMKV().encode("isFirstDialog",false)
            val activity=instrumentation.startActivitySync(android.content.Intent(instrumentation.targetContext,com.mychuannong.app.feature.timetable.ui.TimeTableActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            instrumentation.waitForIdleSync();android.os.SystemClock.sleep(2000)
            val bitmap=instrumentation.uiAutomation.takeScreenshot()
            java.io.File(instrumentation.targetContext.filesDir,"isolated-enrollment-2025.png").outputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
            bitmap.recycle();instrumentation.runOnMainSync{activity.finish()}
        } finally {manager.clear()}
    }
}
