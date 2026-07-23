// 알림 엔티티의 영속화를 제공하는 Spring Data 저장소
package com.safelense.notification

import org.springframework.data.jpa.repository.JpaRepository

interface NotificationRepository : JpaRepository<Notification, Long>
