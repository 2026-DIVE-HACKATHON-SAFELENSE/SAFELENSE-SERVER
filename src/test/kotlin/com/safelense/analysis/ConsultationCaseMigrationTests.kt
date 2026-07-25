// 상담 사례 검색 스키마가 필수 테이블과 제약을 만드는지 검증하는 테스트
package com.safelense.analysis

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource

class ConsultationCaseMigrationTests {
    @Test
    fun `migration creates consultation cases and run matches`() {
        val migration = ClassPathResource("db/migration/V10__create_consultation_case_search.sql")

        assertThat(migration.exists()).isTrue()
        val sql = migration.inputStream.bufferedReader().use { it.readText() }
        assertThat(sql).contains(
            "CREATE TABLE consultation_cases",
            "external_case_id VARCHAR(64) NOT NULL",
            "embedding_json TEXT NULL",
            "CREATE TABLE analysis_case_matches",
            "UNIQUE (run_id, rank)",
            "UNIQUE (run_id, consultation_case_id)",
        )
    }
}
