// 알림 테이블 마이그레이션의 구조와 조회 인덱스를 검증하는 테스트
package com.safelense.notification

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource

class NotificationMigrationTests {
    @Test
    fun `notifications migration defines storage ownership and query indexes`() {
        val migration = ClassPathResource("db/migration/V5__create_notifications.sql")

        assertThat(migration.exists()).isTrue()

        val sql = migration.inputStream.bufferedReader().use { it.readText() }
        assertThat(sql).contains("CREATE TABLE notifications")
        assertThat(sql).contains("id BIGINT NOT NULL AUTO_INCREMENT")
        assertThat(sql).contains("user_id BIGINT NOT NULL")
        assertThat(sql).contains("type VARCHAR(32) NOT NULL")
        assertThat(sql).contains("title VARCHAR(255) NOT NULL")
        assertThat(sql).contains("body TEXT NOT NULL")
        assertThat(sql).contains("target_type VARCHAR(32) NULL")
        assertThat(sql).contains("target_id VARCHAR(255) NULL")
        assertThat(sql).contains("read_at DATETIME(6) NULL")
        assertThat(sql).contains("created_at DATETIME(6) NOT NULL")
        assertThat(sql).contains("FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE")
        assertThat(sql).contains("INDEX idx_notifications_user_id_id (user_id, id)")
        assertThat(sql).contains("INDEX idx_notifications_user_id_read_at_id (user_id, read_at, id)")
    }
}
