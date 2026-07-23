// 내부 알림 생성 규칙과 저장 값을 검증하는 서비스 테스트
package com.safelense.notification

import java.time.Instant
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class NotificationServiceTests {
    private val repository = mock(NotificationRepository::class.java)
    private val service = NotificationService(repository)

    @Test
    fun `creates a notification with normalized content and target`() {
        `when`(repository.save(any(Notification::class.java))).thenAnswer { it.arguments[0] }
        val before = Instant.now()

        val result = service.create(
            7L,
            NotificationCreateCommand(
                type = NotificationType.ANALYSIS,
                title = "  분석이 완료되었어요  ",
                body = "  결과를 확인해 보세요.  ",
                targetType = NotificationTargetType.ANALYSIS_RESULT,
                targetId = "analysis-12",
            ),
        )

        val after = Instant.now()
        val captor = ArgumentCaptor.forClass(Notification::class.java)
        verify(repository).save(captor.capture())
        assertThat(captor.value.userId).isEqualTo(7L)
        assertThat(result.type).isEqualTo(NotificationType.ANALYSIS)
        assertThat(result.title).isEqualTo("분석이 완료되었어요")
        assertThat(result.body).isEqualTo("결과를 확인해 보세요.")
        assertThat(result.targetType).isEqualTo(NotificationTargetType.ANALYSIS_RESULT)
        assertThat(result.targetId).isEqualTo("analysis-12")
        assertThat(result.createdAt).isBetween(before, after)
    }

    @Test
    fun `rejects a blank title`() {
        assertRejected(command(title = "  "))
    }

    @Test
    fun `rejects a blank body`() {
        assertRejected(command(body = "  "))
    }

    @Test
    fun `rejects a title longer than 150 characters`() {
        assertRejected(command(title = "a".repeat(151)))
    }

    @Test
    fun `rejects a body longer than 1000 characters`() {
        assertRejected(command(body = "a".repeat(1001)))
    }

    @Test
    fun `rejects a target type without a target id`() {
        assertRejected(
            command(
                targetType = NotificationTargetType.ANALYSIS_RESULT,
                targetId = null,
            ),
        )
    }

    @Test
    fun `rejects a target id without a target type`() {
        assertRejected(command(targetType = null, targetId = "analysis-12"))
    }

    private fun assertRejected(command: NotificationCreateCommand) {
        assertThatThrownBy { service.create(7L, command) }
            .isInstanceOf(InvalidNotificationCreateCommandException::class.java)
        verify(repository, never()).save(any(Notification::class.java))
    }

    private fun command(
        title: String = "분석이 완료되었어요",
        body: String = "결과를 확인해 보세요.",
        targetType: NotificationTargetType? = null,
        targetId: String? = null,
    ): NotificationCreateCommand =
        NotificationCreateCommand(
            type = NotificationType.ANALYSIS,
            title = title,
            body = body,
            targetType = targetType,
            targetId = targetId,
        )
}
