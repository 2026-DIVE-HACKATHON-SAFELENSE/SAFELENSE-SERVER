// 운영 설정이 SSM 비밀값을 로그에 노출하지 않는지 검증하는 테스트
package com.safelense.config

import java.nio.file.Files
import java.nio.file.Path
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ProductionLoggingTests {
    @Test
    fun `suppresses Hibernate connection metadata that includes the database URL`() {
        val applicationConfiguration = Files.readString(Path.of("src/main/resources/application.yml"))

        assertThat(applicationConfiguration).contains("org.hibernate.orm.connections.pooling: WARN")
    }
}
