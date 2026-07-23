// 사용자별 알림 내용과 읽음 상태를 저장하는 JPA 엔티티
package com.safelense.notification

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

enum class NotificationType {
    ANALYSIS,
    NEWS,
    SYSTEM,
}

enum class NotificationTargetType {
    ANALYSIS_RESULT,
    NEWS_ARTICLE,
}

@Entity
@Table(name = "notifications")
class Notification(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @Column(name = "user_id", nullable = false)
    var userId: Long,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    var type: NotificationType,
    @Column(nullable = false, length = 255)
    var title: String,
    @Column(nullable = false, columnDefinition = "TEXT")
    var body: String,
    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", length = 32)
    var targetType: NotificationTargetType? = null,
    @Column(name = "target_id", length = 255)
    var targetId: String? = null,
    @Column(name = "read_at")
    var readAt: Instant? = null,
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant,
)
