package com.mychuannong.app.school

import androidx.test.platform.app.InstrumentationRegistry
import com.mychuannong.app.common.data.local.mmkv.StudentInfoManager
import com.mychuannong.app.core.privacy.AccountDataManager
import com.mychuannong.app.feature.common.data.local.mmkv.ExamArrangementCache
import com.mychuannong.app.school.education.data.remote.model.ExamArrange
import com.tencent.mmkv.MMKV
import kotlinx.coroutines.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.*
import org.junit.Assert.*

class AuditPrivacyDeviceTest {
    @Before fun setup() = runBlocking {
        Assume.assumeTrue(InstrumentationRegistry.getInstrumentation().targetContext.packageName.endsWith(".bindingtest"))
        SchoolSyncEngine.clear()
        SicauNativeAuth.awaitCleared()
        val owner = "synthetic-audit-a"
        val jar = SicauJiaowuCookieJar()
        jar.acceptRawCookies(SicauJiaowuProtocol.ORIGIN.toHttpUrl(), listOf("ASPSESSIONIDTEST=synthetic; Path=/; Secure; HttpOnly"))
        val client = SicauJiaowuSession.client(jar).newBuilder().addInterceptor { chain ->
            okhttp3.Response.Builder().request(chain.request()).protocol(okhttp3.Protocol.HTTP_1_1)
                .code(200).message("synthetic")
                .header("Content-Type", "text/html; charset=utf-8")
                .body("<table><tr><td>学号</td><td>$owner</td></tr></table>".toResponseBody()).build()
        }.build()
        val session = SicauJiaowuSession(jar, client, {})
        session.fetchPersonalInfo(owner)
        SicauJiaowuAuth.activate(session)
        StudentInfoManager.acceptVerifiedIdentity(owner)
        com.mychuannong.app.core.security.SchoolAccessGate.restoreBinding()
        check(StudentInfoManager.hasCompleteBinding)
    }
    @After fun cleanup() = runBlocking { SchoolSyncEngine.clear() }

    @Test fun examCacheIsAccountScopedAndUnbindingClearsEveryOwner() = runBlocking {
        val cache = ExamArrangementCache()
        val exam = ExamArrange("测试校区", "测试场次", "synthetic", "合成考试", "合成教师",
            "2026-12-01 10:00", null, null, "测试教室", "1", "synthetic-ticket", "")
        cache.saveExamArrangement(listOf(exam))
        assertEquals(listOf(exam), cache.getExamArrangement())
        assertFalse(MMKV.mmkvWithID("content_cache").containsKey("exams"))
        StudentInfoManager.acceptVerifiedIdentity("synthetic-audit-b")
        assertNull(cache.getExamArrangement())
        StudentInfoManager.acceptVerifiedIdentity("synthetic-audit-a")
        assertEquals(listOf(exam), cache.getExamArrangement())
        AccountDataManager.clearSchoolBinding()
        StudentInfoManager.acceptVerifiedIdentity("synthetic-audit-a")
        assertNull(cache.getExamArrangement())
    }

    @Test fun unownedLegacyExamCacheIsNeverAssignedToCurrentUser() {
        MMKV.mmkvWithID("content_cache").encode("exams", "[{\"courseNameval\":\"unowned\",\"examTime\":\"unknown\"}]")
        assertNull(ExamArrangementCache().getExamArrangement())
        assertFalse(MMKV.mmkvWithID("content_cache").containsKey("exams"))
    }

    @Test fun clearLearningSuspendsWhileSyncNeedsTheMainThread() = runBlocking {
        val identityStarted = CompletableDeferred<Unit>()
        val allowMainCallback = CompletableDeferred<Unit>()
        val reader = object : SchoolReader by SicauReader {
            override suspend fun identity(): SchoolRead<String> {
                identityStarted.complete(Unit)
                allowMainCallback.await()
                withContext(Dispatchers.Main) { yield() }
                return SchoolRead.Failure(SyncPhase.EXPIRED, "synthetic expired session")
            }
        }
        val refresh = launch(Dispatchers.IO) { SchoolSyncEngine.perform(reader, true) }
        identityStarted.await()
        withTimeout(5_000) {
            withContext(Dispatchers.Main) {
                allowMainCallback.complete(Unit)
                AccountDataManager.clearLocalLearningData()
            }
            refresh.join()
        }
        val phases = SchoolSyncEngine.items.value.mapValues { it.value.phase }
        assertTrue("Clearing learning data should reset all phases: $phases", phases.values.all { it == SyncPhase.NOT_SYNCED })
        assertTrue(StudentInfoManager.hasCompleteBinding)
    }

    @Test fun cancelledSyncRestoresUnfinishedStatesAndAllowsRetry() = runBlocking {
        val before = SchoolSyncEngine.items.value
        val authBefore = SchoolAuthState.phase.value
        val started = CompletableDeferred<Unit>()
        val stalled = object : SchoolReader by SicauReader {
            override suspend fun identity(): SchoolRead<String> {
                started.complete(Unit)
                awaitCancellation()
            }
        }
        val job = launch(Dispatchers.IO) { SchoolSyncEngine.perform(stalled, true) }
        withTimeout(5_000) { started.await() }
        assertTrue(SchoolSyncEngine.items.value.values.all { it.phase == SyncPhase.LOADING })
        job.cancelAndJoin()
        assertEquals(before, SchoolSyncEngine.items.value)
        assertEquals(authBefore, SchoolAuthState.phase.value)
        assertTrue(StudentInfoManager.hasCompleteBinding)

        var retried = false
        val retry = object : SchoolReader by SicauReader {
            override suspend fun identity(): SchoolRead<String> {
                retried = true
                return SchoolRead.Failure(SyncPhase.FAILED, "synthetic offline")
            }
        }
        SchoolSyncEngine.perform(retry, false)
        assertTrue("Cancellation should not suppress the next automatic retry", retried)
    }
}
