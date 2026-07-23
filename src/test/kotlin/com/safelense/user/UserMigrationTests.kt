// MySQL 사용자 마이그레이션 파일의 존재를 검증하는 테스트
package com.safelense.user

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource

class UserMigrationTests {
    @Test
    fun `users migration exists`() {
        assertThat(ClassPathResource("db/migration/V1__create_users.sql").exists()).isTrue()
    }
}
