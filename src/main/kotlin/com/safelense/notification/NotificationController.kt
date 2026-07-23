// 인증 사용자의 알림 목록 조회와 읽음 처리 API를 제공하는 컨트롤러
package com.safelense.notification

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

data class NotificationListResponse(
    val notifications: List<NotificationResponse>,
    val unreadCount: Long,
    val nextCursor: Long?,
    val hasNext: Boolean,
)

data class NotificationResponse(
    val id: Long,
    val type: NotificationType,
    val title: String,
    val body: String,
    @get:JsonProperty("isRead")
    val isRead: Boolean,
    val createdAt: Instant,
    val targetType: NotificationTargetType?,
    val targetId: String?,
)

@RestController
@RequestMapping("/api/v1/notifications")
class NotificationController(
    private val service: NotificationService,
) {
    @GetMapping
    fun list(
        authentication: Authentication,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(defaultValue = "20") size: String,
        @RequestParam(defaultValue = "false") unreadOnly: Boolean,
    ): NotificationListResponse {
        val page = service.list(
            userId = authentication.principal as Long,
            cursor = cursor?.toLongOrNull() ?: if (cursor == null) null else invalidRequest(),
            size = size.toIntOrNull() ?: invalidRequest(),
            unreadOnly = unreadOnly,
        )
        return NotificationListResponse(
            notifications = page.items.map { it.toResponse() },
            unreadCount = page.unreadCount,
            nextCursor = page.nextCursor,
            hasNext = page.hasNext,
        )
    }

    @PatchMapping("/{notificationId}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun read(
        authentication: Authentication,
        @PathVariable notificationId: String,
    ) {
        service.read(
            authentication.principal as Long,
            notificationId.toLongOrNull() ?: invalidRequest(),
        )
    }

    @PatchMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun readAll(authentication: Authentication) {
        service.readAll(authentication.principal as Long)
    }

    private fun invalidRequest(): Nothing = throw InvalidNotificationRequestException()

    private fun NotificationItem.toResponse(): NotificationResponse =
        NotificationResponse(
            id = id,
            type = type,
            title = title,
            body = body,
            isRead = isRead,
            createdAt = createdAt,
            targetType = targetType,
            targetId = targetId,
        )
}
