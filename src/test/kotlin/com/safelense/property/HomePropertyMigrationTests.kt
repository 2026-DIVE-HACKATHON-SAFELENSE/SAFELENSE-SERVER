// 내 집 정보 테이블 마이그레이션의 핵심 제약 조건을 검증하는 테스트
package com.safelense.property

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource

class HomePropertyMigrationTests {
    @Test
    fun `home properties migration defines one property per user`() {
        val migration = ClassPathResource("db/migration/V3__create_home_properties.sql")

        assertThat(migration.exists()).isTrue()

        val sql = migration.inputStream.bufferedReader().use { it.readText() }
        assertThat(sql).contains("CONSTRAINT uk_home_properties_user_id UNIQUE (user_id)")
        assertThat(sql).contains("FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE")
    }
}
