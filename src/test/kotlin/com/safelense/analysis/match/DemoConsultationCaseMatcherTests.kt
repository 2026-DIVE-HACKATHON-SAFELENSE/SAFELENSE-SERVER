// 데모 상담 사례 매처가 위험 사실 키만 사용해 비식별 패턴을 반환하는지 검증하는 테스트
package com.safelense.analysis.match

import com.safelense.analysis.collection.CollectedEvidenceCommand
import com.safelense.analysis.evidence.EvidenceStatus
import java.time.Instant
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DemoConsultationCaseMatcherTests {
    private val matcher: ConsultationCaseMatcher = DemoConsultationCaseMatcher()

    @Test
    fun `matches only anonymized patterns from available risk evidence`() {
        val matches = matcher.match(
            listOf(
                evidence("JEONSE_RATIO", """{"ratio":0.82}""", EvidenceStatus.AVAILABLE),
                evidence("DEPOSIT_INSURANCE_ELIGIBILITY", null, EvidenceStatus.UNAVAILABLE),
            ),
        )

        assertThat(matches).isNotEmpty
        assertThat(matches).allMatch { it.caseId.startsWith("DEMO-") }
        assertThat(matches).allMatch { it.similarity in 0.0..1.0 }
        assertThat(matches).allMatch { !it.summary.contains("임대인") }
        assertThat(matches).allMatch { !it.summary.contains("서울") }
    }

    @Test
    fun `does not match patterns when no risk fact is available`() {
        val matches = matcher.match(
            listOf(evidence("OFFICIAL_PRICE", null, EvidenceStatus.NOT_AVAILABLE)),
        )

        assertThat(matches).isEmpty()
    }

    private fun evidence(key: String, valueJson: String?, status: EvidenceStatus) =
        CollectedEvidenceCommand(
            evidenceKey = key,
            valueJson = valueJson,
            source = "DEMO",
            sourceIdentifier = "demo-seed-2026-v1",
            asOf = Instant.parse("2026-07-01T00:00:00Z"),
            collectedAt = Instant.parse("2026-07-26T00:00:00Z"),
            confidence = if (status == EvidenceStatus.AVAILABLE) 80 else 0,
            status = status,
        )
}
