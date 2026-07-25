// 구조화 후보와 의미 유사도를 결합해 실제 상담 사례를 선택하는 테스트
package com.safelense.analysis.match

import com.safelense.analysis.AnalysisRiskAssessment
import com.safelense.analysis.AnalysisRiskGrade
import com.safelense.analysis.collection.CollectedEvidenceCommand
import com.safelense.analysis.evidence.EvidenceStatus
import com.safelense.property.BuildingType
import com.safelense.property.HomeProperty
import java.time.Instant
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import tools.jackson.databind.ObjectMapper

class HybridConsultationCaseMatcherTests {
    private val repository = mock(ConsultationCaseRepository::class.java)
    private val objectMapper = ObjectMapper()

    @Test
    fun `returns top three cases above the fixed threshold`() {
        `when`(repository.findAll()).thenReturn(
            listOf(
                case(1, "1억~2억", "아파트", "서울", "[1.0,0.0]"),
                case(2, "1억~2억", "아파트", "서울", "[0.9,0.1]"),
                case(3, "1억~2억", "아파트", "경기", "[0.8,0.2]"),
                case(4, "3억 이상", "오피스텔", "서울", "[0.0,1.0]"),
            ),
        )
        val matcher = HybridConsultationCaseMatcher(
            repository,
            EmbeddingClient { listOf(listOf(1.0, 0.0)) },
            ConsultationStructuredScorer(),
            objectMapper,
        )

        val result = matcher.match(request())

        assertThat(result.degraded).isFalse()
        assertThat(result.cases).hasSize(3)
        assertThat(result.cases.map { it.caseId }).containsExactly("1", "2", "3")
        assertThat(result.cases.map { it.combinedScore }).isSortedAccordingTo(reverseOrder())
        assertThat(result.cases).allMatch { it.semanticScore != null }
        assertThat(result.cases).allMatch { !it.summary.contains("개별 상담 원문") }
    }

    @Test
    fun `falls back to structured scores when query embedding fails`() {
        `when`(repository.findAll()).thenReturn(
            listOf(case(1, "1억~2억", "아파트", "서울", "[1.0,0.0]")),
        )
        val matcher = HybridConsultationCaseMatcher(
            repository,
            EmbeddingClient { throw EmbeddingUnavailableException() },
            ConsultationStructuredScorer(),
            objectMapper,
        )

        val result = matcher.match(request())

        assertThat(result.degraded).isTrue()
        assertThat(result.cases.single().semanticScore).isNull()
        assertThat(result.cases.single().combinedScore).isEqualTo(1.0)
    }

    private fun request() =
        ConsultationMatchRequest(
            property = HomeProperty(
                id = 2L,
                userId = 1L,
                address = "외부로 전달하지 않을 주소",
                depositAmount = 15000,
                buildingType = BuildingType.APARTMENT,
            ),
            evidence = listOf(
                CollectedEvidenceCommand(
                    evidenceKey = "ADDRESS_RESOLUTION",
                    valueJson = """{"province":"서울특별시","district":"중구","legalDong":"태평로1가"}""",
                    source = "VWORLD_ADDRESS",
                    sourceIdentifier = null,
                    asOf = null,
                    collectedAt = Instant.parse("2026-07-26T00:00:00Z"),
                    confidence = 100,
                    status = EvidenceStatus.AVAILABLE,
                ),
            ),
            assessment = AnalysisRiskAssessment(
                score = 40,
                grade = AnalysisRiskGrade.MEDIUM,
                confidence = 35,
                summary = "추가 확인이 필요한 계약입니다.",
                findings = listOf("담보비율을 확인했습니다."),
                recommendations = listOf("공시가격을 확인하세요."),
                ruleVersion = "dive-2026-v1",
            ),
        )

    private fun case(
        id: Long,
        depositBand: String,
        housingType: String,
        province: String,
        embeddingJson: String,
    ) = ConsultationCase(
        id = id,
        externalCaseId = id.toString(),
        source = CONSULTATION_SOURCE,
        datasetVersion = CONSULTATION_DATASET_VERSION,
        sourceGroup = "임차in",
        consultationMonth = "2026-01",
        province = province,
        district = "중구",
        depositBand = depositBand,
        contractStatus = "계약전",
        housingType = housingType,
        seniorRights = "미상",
        guaranteeStatus = "미상",
        disputeType = "보증금반환",
        progressStage = "상담",
        situationSummary = "개별 상담 원문",
        embeddingJson = embeddingJson,
    )
}
