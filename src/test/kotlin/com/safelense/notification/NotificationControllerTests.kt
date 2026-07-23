// 알림 목록과 읽음 처리의 HTTP 계약을 검증하는 MVC 테스트
package com.safelense.notification

import com.safelense.auth.presentation.ApiExceptionHandler
import java.time.Instant
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class NotificationControllerTests {
    private val service = mock(NotificationService::class.java)
    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders
            .standaloneSetup(NotificationController(service))
            .setControllerAdvice(ApiExceptionHandler())
            .setMessageConverters(JacksonJsonHttpMessageConverter())
            .build()
    }

    @Test
    fun `lists notifications with HTTP defaults and complete response fields`() {
        val page = NotificationPage(
            items = listOf(
                NotificationItem(
                    id = 31L,
                    type = NotificationType.ANALYSIS,
                    title = "등기부등본 분석 완료",
                    body = "분석 결과를 확인해 주세요.",
                    isRead = false,
                    createdAt = Instant.parse("2026-07-24T10:15:30Z"),
                    targetType = NotificationTargetType.ANALYSIS_RESULT,
                    targetId = "analysis-31",
                ),
            ),
            nextCursor = 31L,
            hasNext = true,
            unreadCount = 4L,
        )
        `when`(service.list(7L, null, 20, false)).thenReturn(page)

        mockMvc.perform(
            get("/api/v1/notifications")
                .principal(authentication()),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.notifications[0].id").value(31))
            .andExpect(jsonPath("$.notifications[0].type").value("ANALYSIS"))
            .andExpect(jsonPath("$.notifications[0].title").value("등기부등본 분석 완료"))
            .andExpect(jsonPath("$.notifications[0].body").value("분석 결과를 확인해 주세요."))
            .andExpect(jsonPath("$.notifications[0].isRead").value(false))
            .andExpect(jsonPath("$.notifications[0].createdAt").value("2026-07-24T10:15:30Z"))
            .andExpect(jsonPath("$.notifications[0].targetType").value("ANALYSIS_RESULT"))
            .andExpect(jsonPath("$.notifications[0].targetId").value("analysis-31"))
            .andExpect(jsonPath("$.unreadCount").value(4))
            .andExpect(jsonPath("$.nextCursor").value(31))
            .andExpect(jsonPath("$.hasNext").value(true))

        verify(service).list(7L, null, 20, false)
    }

    @Test
    fun `passes explicit cursor size and unread filter`() {
        `when`(service.list(7L, 100L, 5, true))
            .thenReturn(NotificationPage(emptyList(), null, false, 2L))

        mockMvc.perform(
            get("/api/v1/notifications")
                .principal(authentication())
                .param("cursor", "100")
                .param("size", "5")
                .param("unreadOnly", "true"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.notifications").isEmpty)
            .andExpect(jsonPath("$.unreadCount").value(2))
            .andExpect(jsonPath("$.nextCursor").doesNotExist())
            .andExpect(jsonPath("$.hasNext").value(false))

        verify(service).list(7L, 100L, 5, true)
    }

    @Test
    fun `returns invalid request for out of range cursor and sizes`() {
        val invalidQueries = listOf(
            "cursor=0" to { `when`(service.list(7L, 0L, 20, false)).thenThrow(InvalidNotificationRequestException()) },
            "size=0" to { `when`(service.list(7L, null, 0, false)).thenThrow(InvalidNotificationRequestException()) },
            "size=101" to { `when`(service.list(7L, null, 101, false)).thenThrow(InvalidNotificationRequestException()) },
        )

        invalidQueries.forEach { (query, stub) ->
            stub()

            mockMvc.perform(
                get("/api/v1/notifications?$query")
                    .principal(authentication()),
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
        }
    }

    @Test
    fun `returns invalid request for malformed numeric parameters`() {
        listOf("cursor=invalid", "size=invalid").forEach { query ->
            mockMvc.perform(
                get("/api/v1/notifications?$query")
                    .principal(authentication()),
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
        }

        mockMvc.perform(
            patch("/api/v1/notifications/invalid/read")
                .principal(authentication()),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))

        verifyNoInteractions(service)
    }

    @Test
    fun `marks one owned notification as read`() {
        mockMvc.perform(
            patch("/api/v1/notifications/41/read")
                .principal(authentication()),
        )
            .andExpect(status().isNoContent)

        verify(service).read(7L, 41L)
    }

    @Test
    fun `marks all owned notifications as read`() {
        mockMvc.perform(
            patch("/api/v1/notifications/read-all")
                .principal(authentication()),
        )
            .andExpect(status().isNoContent)

        verify(service).readAll(7L)
    }

    @Test
    fun `hides a notification not owned by the authenticated user`() {
        doThrow(NotificationNotFoundException())
            .`when`(service)
            .read(7L, 41L)

        mockMvc.perform(
            patch("/api/v1/notifications/41/read")
                .principal(authentication()),
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("NOTIFICATION_NOT_FOUND"))
    }

    private fun authentication() = UsernamePasswordAuthenticationToken(7L, null)
}
