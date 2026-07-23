// 내부 알림 생성 명령을 검증하고 저장하는 서비스
package com.safelense.notification

import java.time.Instant
import org.springframework.stereotype.Service
import org.springframework.data.domain.PageRequest
import org.springframework.transaction.annotation.Transactional

data class NotificationCreateCommand(
    val type: NotificationType,
    val title: String,
    val body: String,
    val targetType: NotificationTargetType? = null,
    val targetId: String? = null,
)

class InvalidNotificationRequestException : RuntimeException()

class NotificationNotFoundException : RuntimeException()

data class NotificationItem(
    val id: Long,
    val type: NotificationType,
    val title: String,
    val body: String,
    val isRead: Boolean,
    val createdAt: Instant,
    val targetType: NotificationTargetType?,
    val targetId: String?,
)

data class NotificationPage(
    val items: List<NotificationItem>,
    val nextCursor: Long?,
    val hasNext: Boolean,
    val unreadCount: Long,
)

@Service
class NotificationService(
    private val notificationRepository: NotificationRepository,
) {
    @Transactional
    fun create(userId: Long, command: NotificationCreateCommand): Notification {
        val title = command.title.trim()
        val body = command.body.trim()
        val targetId = command.targetId?.trim()
        if (title.isEmpty() || title.length > 150 || body.isEmpty() || body.length > 1000) {
            throw InvalidNotificationRequestException()
        }
        if ((command.targetType == null) != (targetId == null) || targetId?.isEmpty() == true) {
            throw InvalidNotificationRequestException()
        }

        return notificationRepository.save(
            Notification(
                userId = userId,
                type = command.type,
                title = title,
                body = body,
                targetType = command.targetType,
                targetId = targetId,
                createdAt = Instant.now(),
            ),
        )
    }

    @Transactional(readOnly = true)
    fun list(userId: Long, cursor: Long?, size: Int, unreadOnly: Boolean): NotificationPage {
        if (cursor != null && cursor <= 0 || size !in 1..100) {
            throw InvalidNotificationRequestException()
        }

        val notifications = notificationRepository.findByUserIdWithCursor(
            userId = userId,
            cursor = cursor,
            unreadOnly = unreadOnly,
            pageable = PageRequest.of(0, size + 1),
        )
        val hasNext = notifications.size > size
        val items = notifications.take(size).map { notification ->
            NotificationItem(
                id = requireNotNull(notification.id),
                type = notification.type,
                title = notification.title,
                body = notification.body,
                isRead = notification.readAt != null,
                createdAt = notification.createdAt,
                targetType = notification.targetType,
                targetId = notification.targetId,
            )
        }

        return NotificationPage(
            items = items,
            nextCursor = if (hasNext) items.last().id else null,
            hasNext = hasNext,
            unreadCount = notificationRepository.countByUserIdAndReadAtIsNull(userId),
        )
    }

    @Transactional
    fun read(userId: Long, notificationId: Long) {
        val updatedCount = notificationRepository.markAsReadIfUnread(userId, notificationId, Instant.now())
        if (updatedCount == 0 && !notificationRepository.existsByIdAndUserId(notificationId, userId)) {
            throw NotificationNotFoundException()
        }
    }

    @Transactional
    fun readAll(userId: Long) {
        notificationRepository.markAllAsReadIfUnread(userId, Instant.now())
    }
}
