// 인증 사용자의 알림 목록 조회와 읽음 처리 API를 제공하는 컨트롤러
package com.safelense.notification

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
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

@Schema(description = "알림 목록과 전체 미읽음 개수")
data class NotificationListResponse(
    @field:Schema(description = "요청 조건에 맞는 알림 목록")
    val notifications: List<NotificationResponse>,
    @field:Schema(description = "페이지 조건과 관계없는 전체 미읽음 알림 개수", example = "3")
    val unreadCount: Long,
    val nextCursor: Long?,
    val hasNext: Boolean,
)

@Schema(description = "사용자에게 전달된 알림 한 건")
data class NotificationResponse(
    @field:Schema(description = "알림 ID", example = "101")
    val id: Long,
    @field:Schema(description = "알림 유형", example = "ANALYSIS")
    val type: NotificationType,
    @field:Schema(description = "알림 제목")
    val title: String,
    @field:Schema(description = "알림 본문")
    val body: String,
    @get:JsonProperty("isRead")
    @field:Schema(description = "읽음 여부")
    val isRead: Boolean,
    @field:Schema(description = "알림 생성 시각")
    val createdAt: Instant,
    val targetType: NotificationTargetType?,
    val targetId: String?,
)

@Tag(name = "알림", description = "사용자 알림 목록을 조회하고 읽음 상태를 처리합니다.")
@RestController
@RequestMapping("/api/v1/notifications")
class NotificationController(
    private val service: NotificationService,
) {
    @Operation(summary = "알림 목록 조회", description = "커서와 읽음 여부 조건으로 알림 목록과 전체 미읽음 개수를 조회합니다.")
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

    @Operation(summary = "알림 한 건 읽음 처리", description = "지정한 알림을 읽음으로 표시합니다. 이미 읽은 알림도 안전하게 다시 요청할 수 있습니다.")
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

    @Operation(summary = "알림 모두 읽음 처리", description = "현재 사용자의 읽지 않은 알림을 모두 읽음으로 표시합니다.")
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
