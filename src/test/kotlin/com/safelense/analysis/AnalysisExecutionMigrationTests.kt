// 분석 실행 감사 필드 마이그레이션 계약을 검증하는 테스트
package com.safelense.analysis

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource

class AnalysisExecutionMigrationTests {
    @Test
    fun `analysis execution migration adds idempotency key and input snapshot`() {
        val migration = ClassPathResource("db/migration/V7__add_analysis_execution_audit.sql")

        assertThat(migration.exists()).isTrue()
        val sql = migration.inputStream.bufferedReader().use { it.readText() }
        assertThat(sql).contains("ALTER TABLE analysis_results")
        assertThat(sql).contains("ADD COLUMN idempotency_key VARCHAR(100) NULL")
        assertThat(sql).contains("ADD COLUMN input_snapshot TEXT NULL")
    }
}
