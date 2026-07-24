// 분석 결과 테이블의 소유권과 조회 제약을 검증하는 테스트
package com.safelense.analysis

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource

class AnalysisResultMigrationTests {
    @Test
    fun `analysis result migration defines ownership and history indexes`() {
        val migration = ClassPathResource("db/migration/V6__create_analysis_results.sql")

        assertThat(migration.exists()).isTrue()
        val sql = migration.inputStream.bufferedReader().use { it.readText() }
        assertThat(sql).contains("CREATE TABLE analysis_results")
        assertThat(sql).contains("UNIQUE (case_id)")
        assertThat(sql).contains("CREATE INDEX idx_analysis_results_user_id_id ON analysis_results (user_id, id)")
        assertThat(sql).contains("CREATE INDEX idx_analysis_results_user_stage_id ON analysis_results (user_id, stage, id)")
        assertThat(sql).contains("FOREIGN KEY (case_id) REFERENCES analysis_cases(id)")
        assertThat(sql).contains("FOREIGN KEY (user_id) REFERENCES users(id)")
    }
}
