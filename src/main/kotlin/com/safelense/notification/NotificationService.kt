// 내부 알림 생성 명령을 검증하고 저장하는 서비스
package com.safelense.notification

import java.time.Instant
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

data class NotificationCreateCommand(
    val type: NotificationType,
    val title: String,
    val body: String,
    val targetType: NotificationTargetType? = null,
    val targetId: String? = null,
)

class InvalidNotificationCreateCommandException : RuntimeException()

@Service
class NotificationService(
    private val notificationRepository: NotificationRepository,
) {
    @Transactional
    fun create(userId: Long, command: NotificationCreateCommand): Notification {
        val title = command.title.trim()
        val body = command.body.trim()
        if (title.isEmpty() || title.length > 150 || body.isEmpty() || body.length > 1000) {
            throw InvalidNotificationCreateCommandException()
        }
        if ((command.targetType == null) != (command.targetId == null)) {
            throw InvalidNotificationCreateCommandException()
        }

        return notificationRepository.save(
            Notification(
                userId = userId,
                type = command.type,
                title = title,
                body = body,
                targetType = command.targetType,
                targetId = command.targetId,
                createdAt = Instant.now(),
            ),
        )
    }
}
