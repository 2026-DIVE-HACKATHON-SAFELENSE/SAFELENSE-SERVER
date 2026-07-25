// 등기부 객체 참조 저장소 마이그레이션 계약을 검증하는 테스트
package com.safelense.document

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource

class RegistryDocumentMigrationTests {
    @Test
    fun `registry document migration stores object metadata without file content`() {
        val migration = ClassPathResource("db/migration/V9__create_registry_documents.sql")

        assertThat(migration.exists()).isTrue()
        val sql = migration.inputStream.bufferedReader().use { it.readText() }
        assertThat(sql).contains("CREATE TABLE registry_documents", "storage_key", "sha256", "expires_at")
        assertThat(sql).doesNotContain("BYTEA", "content")
    }
}
