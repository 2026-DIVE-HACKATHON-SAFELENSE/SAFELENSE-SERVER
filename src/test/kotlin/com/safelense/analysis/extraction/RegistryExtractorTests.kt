// 등기부 미제출과 사용 불가 상태를 명시적 근거로 변환하는지 검증하는 테스트
package com.safelense.analysis.extraction

import com.safelense.analysis.evidence.EvidenceStatus
import com.safelense.document.RegistryDocument
import com.safelense.document.RegistryExtractionStatus
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RegistryExtractorTests {
    private val extractor: RegistryExtractor = DemoRegistryExtractor(
        Clock.fixed(Instant.parse("2026-07-26T00:00:00Z"), ZoneOffset.UTC),
    )

    @Test
    fun `returns explicit missing evidence when no registry document was uploaded`() {
        val evidence = extractor.extract(null)

        val single = evidence.single()
        assertThat(single.evidenceKey).isEqualTo("REGISTRY_DOCUMENT")
        assertThat(single.status).isEqualTo(EvidenceStatus.NOT_AVAILABLE)
        assertThat(single.valueJson).isNull()
    }

    @Test
    fun `returns unavailable evidence for an expired registry document`() {
        val evidence = extractor.extract(
            RegistryDocument(
                id = 3L,
                propertyId = 2L,
                storageKey = "private/registry/a.pdf",
                sha256 = "a".repeat(64),
                mimeType = "application/pdf",
                fileSize = 3,
                extractionStatus = RegistryExtractionStatus.EXPIRED,
                expiresAt = Instant.parse("2026-07-25T00:00:00Z"),
            ),
        )

        val single = evidence.single()
        assertThat(single.status).isEqualTo(EvidenceStatus.UNAVAILABLE)
        assertThat(single.sourceIdentifier).isEqualTo("a".repeat(64))
    }
}
