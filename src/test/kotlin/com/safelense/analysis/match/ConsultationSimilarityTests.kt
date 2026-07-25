// 알려진 상담 조건만 분모에 포함하는 구조화·결합 유사도 계산 테스트
package com.safelense.analysis.match

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test

class ConsultationSimilarityTests {
    private val scorer = ConsultationStructuredScorer()

    @Test
    fun `excludes unknown criteria from the structured denominator`() {
        val query = ConsultationFeatures(
            depositBand = "1억~2억",
            housingTypes = setOf("아파트"),
            seniorRights = null,
            guaranteeStatus = null,
            province = "서울",
        )

        val score = scorer.score(query, case())

        assertThat(score).isEqualTo(1.0)
    }

    @Test
    fun `uses fixed structured and semantic weights`() {
        assertThat(combineConsultationScores(structured = 0.8, semantic = 0.6))
            .isCloseTo(0.71, within(1e-12))
    }

    private fun case() =
        ConsultationCase(
            externalCaseId = "1",
            source = CONSULTATION_SOURCE,
            datasetVersion = CONSULTATION_DATASET_VERSION,
            sourceGroup = "임차in",
            consultationMonth = "2026-01",
            province = "서울",
            district = "중구",
            depositBand = "1억~2억",
            contractStatus = "계약전",
            housingType = "아파트",
            seniorRights = "미상",
            guaranteeStatus = "미상",
            disputeType = "보증금반환",
            progressStage = "상담",
        )
}
