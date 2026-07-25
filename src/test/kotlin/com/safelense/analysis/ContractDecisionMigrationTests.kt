// 계약 전 의사결정 분석 저장소 마이그레이션 계약을 검증하는 테스트
package com.safelense.analysis

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource

class ContractDecisionMigrationTests {
    @Test
    fun `contract decision migration creates runs evidence and reports`() {
        val migration = ClassPathResource("db/migration/V8__create_contract_decision_analysis.sql")

        assertThat(migration.exists()).isTrue()
        val sql = migration.inputStream.bufferedReader().use { it.readText() }
        assertThat(sql).contains(
            "CREATE TABLE analysis_runs",
            "CREATE TABLE collected_evidence",
            "CREATE TABLE analysis_reports",
            "UNIQUE (property_id, idempotency_key)",
            "FOREIGN KEY (property_id) REFERENCES home_properties(id)",
            "FOREIGN KEY (run_id) REFERENCES analysis_runs(id)",
        )
    }
}
