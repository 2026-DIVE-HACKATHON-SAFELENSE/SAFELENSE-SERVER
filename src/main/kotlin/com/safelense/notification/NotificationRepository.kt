// 알림 엔티티의 영속화를 제공하는 Spring Data 저장소
package com.safelense.notification

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.data.domain.Pageable

interface NotificationRepository : JpaRepository<Notification, Long> {
    @Query(
        """
        SELECT n FROM Notification n
        WHERE n.userId = :userId
          AND (:cursor IS NULL OR n.id < :cursor)
          AND (:unreadOnly = false OR n.readAt IS NULL)
        ORDER BY n.id DESC
        """,
    )
    fun findByUserIdWithCursor(
        @Param("userId") userId: Long,
        @Param("cursor") cursor: Long?,
        @Param("unreadOnly") unreadOnly: Boolean,
        pageable: Pageable,
    ): List<Notification>

    fun countByUserIdAndReadAtIsNull(userId: Long): Long
}
