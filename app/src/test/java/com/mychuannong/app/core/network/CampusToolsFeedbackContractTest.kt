package com.mychuannong.app.core.network

import com.mychuannong.app.core.network.campustools.ReservationRules
import com.mychuannong.app.core.network.campustools.RemoteFeedback
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CampusToolsFeedbackContractTest {
    private val gson = Gson()

    @Test
    fun feedbackResponseKeepsPublicReplyAndNeverNeedsInternalNote() {
        val response = gson.fromJson(
            """
            {
              "id":"feedback-1",
              "category":"ACCOUNT_SECURITY",
              "title":"账号问题",
              "content":"无法登录",
              "status":"RESOLVED",
              "publicReply":"已经处理",
              "createdAt":1,
              "updatedAt":2
            }
            """.trimIndent(),
            RemoteFeedback::class.java,
        )

        assertEquals("已经处理", response.publicReply)
        assertEquals("RESOLVED", response.status)
        assertFalse(gson.toJson(response).contains("internalNote"))
    }

    @Test
    fun reservationProfileModeUsesServerValue() {
        val required = gson.fromJson(
            """{"reservationProfileMode":"REQUIRED","requireReservationStudentId":true}""",
            ReservationRules::class.java,
        )
        val optional = gson.fromJson(
            """{"reservationProfileMode":"OPTIONAL","requireReservationStudentId":false}""",
            ReservationRules::class.java,
        )

        assertEquals("REQUIRED", required.reservationProfileMode)
        assertEquals("OPTIONAL", optional.reservationProfileMode)
    }
}
