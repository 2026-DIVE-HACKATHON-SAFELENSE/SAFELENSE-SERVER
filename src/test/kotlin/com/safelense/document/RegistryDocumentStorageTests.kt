// 등기부 원본 저장소의 객체 참조 계약을 검증하는 테스트
package com.safelense.document

import java.time.Instant
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RegistryDocumentStorageTests {
    @Test
    fun `stored registry document exposes only an internal storage key`() {
        val stored = StoredRegistryDocument("registry/1/document.pdf", Instant.parse("2026-08-25T00:00:00Z"))

        assertThat(stored.storageKey).startsWith("registry/")
        assertThat(stored.expiresAt).isAfter(Instant.parse("2026-07-26T00:00:00Z"))
    }
}
