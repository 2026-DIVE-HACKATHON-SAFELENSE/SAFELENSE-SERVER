// 분석 케이스 입력 테이블의 핵심 제약 조건을 검증하는 테스트
package com.safelense.analysis

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource

class AnalysisInputMigrationTests {
    @Test
    fun `analysis input migration defines ownership and slot constraints`() {
        val migration = ClassPathResource("db/migration/V4__create_analysis_case_inputs.sql")

        assertThat(migration.exists()).isTrue()
        val sql = migration.inputStream.bufferedReader().use { it.readText() }
        assertThat(sql).contains("CREATE TABLE analysis_cases")
        assertThat(sql).contains("CREATE TABLE analysis_documents")
        assertThat(sql).contains("CREATE TABLE analysis_checklist_answers")
        assertThat(sql).contains("MEDIUMBLOB NOT NULL")
        assertThat(sql).contains("UNIQUE (case_id, document_type)")
        assertThat(sql).contains("UNIQUE (case_id, item_key)")
        assertThat(sql).contains("FOREIGN KEY (property_id) REFERENCES home_properties(id)")
    }
}
