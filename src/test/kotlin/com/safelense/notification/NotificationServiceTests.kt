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
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.springframework.data.domain.PageRequest

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
                targetId = "  analysis-12  ",
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

    @Test
    fun `rejects an empty target id`() {
        assertRejected(
            command(
                targetType = NotificationTargetType.ANALYSIS_RESULT,
                targetId = "",
            ),
        )
    }

    @Test
    fun `rejects a blank target id`() {
        assertRejected(
            command(
                targetType = NotificationTargetType.ANALYSIS_RESULT,
                targetId = "  ",
            ),
        )
    }

    @Test
    fun `lists size items and provides next cursor when another notification exists`() {
        `when`(
            repository.findByUserIdWithCursor(
                7L,
                null,
                false,
                PageRequest.of(0, 3),
            ),
        ).thenReturn(
            listOf(
                notification(id = 30L, targetType = NotificationTargetType.ANALYSIS_RESULT, targetId = "analysis-30"),
                notification(id = 20L, readAt = Instant.parse("2026-07-20T10:00:00Z")),
                notification(id = 10L),
            ),
        )
        `when`(repository.countByUserIdAndReadAtIsNull(7L)).thenReturn(2L)

        val result = service.list(userId = 7L, cursor = null, size = 2, unreadOnly = false)

        assertThat(result.items).hasSize(2)
        assertThat(result.items.map { it.id }).containsExactly(30L, 20L)
        assertThat(result.hasNext).isTrue()
        assertThat(result.nextCursor).isEqualTo(20L)
        assertThat(result.unreadCount).isEqualTo(2L)
        assertThat(result.items[0])
            .extracting(
                NotificationItem::id,
                NotificationItem::type,
                NotificationItem::title,
                NotificationItem::body,
                NotificationItem::isRead,
                NotificationItem::createdAt,
                NotificationItem::targetType,
                NotificationItem::targetId,
            )
            .containsExactly(
                30L,
                NotificationType.ANALYSIS,
                "분석이 완료되었어요",
                "결과를 확인해 보세요.",
                false,
                Instant.parse("2026-07-20T09:00:00Z"),
                NotificationTargetType.ANALYSIS_RESULT,
                "analysis-30",
            )
        verify(repository).findByUserIdWithCursor(7L, null, false, PageRequest.of(0, 3))
    }

    @Test
    fun `returns null next cursor on the final filtered page and counts all unread notifications`() {
        `when`(
            repository.findByUserIdWithCursor(
                7L,
                30L,
                true,
                PageRequest.of(0, 3),
            ),
        ).thenReturn(listOf(notification(id = 20L)))
        `when`(repository.countByUserIdAndReadAtIsNull(7L)).thenReturn(4L)

        val result = service.list(userId = 7L, cursor = 30L, size = 2, unreadOnly = true)

        assertThat(result.items.map { it.id }).containsExactly(20L)
        assertThat(result.hasNext).isFalse()
        assertThat(result.nextCursor).isNull()
        assertThat(result.unreadCount).isEqualTo(4L)
    }

    @Test
    fun `rejects nonpositive cursor and out of range size`() {
        assertThatThrownBy { service.list(userId = 7L, cursor = 0L, size = 20, unreadOnly = false) }
            .isInstanceOf(InvalidNotificationRequestException::class.java)
        assertThatThrownBy { service.list(userId = 7L, cursor = -1L, size = 20, unreadOnly = false) }
            .isInstanceOf(InvalidNotificationRequestException::class.java)
        assertThatThrownBy { service.list(userId = 7L, cursor = null, size = 0, unreadOnly = false) }
            .isInstanceOf(InvalidNotificationRequestException::class.java)
        assertThatThrownBy { service.list(userId = 7L, cursor = null, size = 101, unreadOnly = false) }
            .isInstanceOf(InvalidNotificationRequestException::class.java)
        verifyNoInteractions(repository)
    }

    @Test
    fun `marks an unread notification as read`() {
        `when`(repository.markAsReadIfUnread(eqLong(7L), eqLong(11L), anyInstant())).thenReturn(1)

        service.read(userId = 7L, notificationId = 11L)

        verify(repository).markAsReadIfUnread(eqLong(7L), eqLong(11L), anyInstant())
        verify(repository, never()).existsByIdAndUserId(11L, 7L)
    }

    @Test
    fun `succeeds when a notification already read by its owner is read again`() {
        `when`(repository.markAsReadIfUnread(eqLong(7L), eqLong(11L), anyInstant())).thenReturn(0)
        `when`(repository.existsByIdAndUserId(11L, 7L)).thenReturn(true)

        service.read(userId = 7L, notificationId = 11L)

        verify(repository).markAsReadIfUnread(eqLong(7L), eqLong(11L), anyInstant())
        verify(repository).existsByIdAndUserId(11L, 7L)
    }

    @Test
    fun `throws when the notification does not exist`() {
        `when`(repository.markAsReadIfUnread(eqLong(7L), eqLong(11L), anyInstant())).thenReturn(0)
        `when`(repository.existsByIdAndUserId(11L, 7L)).thenReturn(false)

        assertThatThrownBy { service.read(userId = 7L, notificationId = 11L) }
            .isInstanceOf(NotificationNotFoundException::class.java)
    }

    @Test
    fun `throws when the notification belongs to another user`() {
        `when`(repository.markAsReadIfUnread(eqLong(7L), eqLong(11L), anyInstant())).thenReturn(0)
        `when`(repository.existsByIdAndUserId(11L, 7L)).thenReturn(false)

        assertThatThrownBy { service.read(userId = 7L, notificationId = 11L) }
            .isInstanceOf(NotificationNotFoundException::class.java)
    }

    @Test
    fun `marks all unread notifications as read even when none exist`() {
        service.readAll(userId = 7L)

        verify(repository).markAllAsReadIfUnread(eqLong(7L), anyInstant())
    }

    private fun assertRejected(command: NotificationCreateCommand) {
        assertThatThrownBy { service.create(7L, command) }
            .isInstanceOf(InvalidNotificationRequestException::class.java)
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

    private fun notification(
        id: Long,
        readAt: Instant? = null,
        targetType: NotificationTargetType? = null,
        targetId: String? = null,
    ): Notification =
        Notification(
            id = id,
            userId = 7L,
            type = NotificationType.ANALYSIS,
            title = "분석이 완료되었어요",
            body = "결과를 확인해 보세요.",
            targetType = targetType,
            targetId = targetId,
            readAt = readAt,
            createdAt = Instant.parse("2026-07-20T09:00:00Z"),
        )

    private fun anyInstant(): Instant {
        any(Instant::class.java)
        return Instant.EPOCH
    }

    private fun eqLong(value: Long): Long {
        org.mockito.ArgumentMatchers.eq(value)
        return value
    }
}
