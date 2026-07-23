// 분석 케이스 생성과 사용자별 입력 상태 조회를 검증하는 서비스 테스트
package com.safelense.analysis

import com.safelense.property.BuildingType
import com.safelense.property.HomeProperty
import com.safelense.property.HomePropertyNotFoundException
import com.safelense.property.HomePropertyRepository
import java.time.LocalDate
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class AnalysisCaseServiceTests {
    private val propertyRepository = mock(HomePropertyRepository::class.java)
    private val caseRepository = mock(AnalysisCaseRepository::class.java)
    private val documentRepository = mock(AnalysisDocumentRepository::class.java)
    private val answerRepository = mock(AnalysisChecklistAnswerRepository::class.java)
    private val service = AnalysisCaseService(
        propertyRepository,
        caseRepository,
        documentRepository,
        answerRepository,
        AnalysisTemplateCatalog(),
    )

    @Test
    fun `creates a case only for the users property`() {
        `when`(propertyRepository.findByIdAndUserId(3L, 7L)).thenReturn(property())
        `when`(caseRepository.save(any(AnalysisCase::class.java))).thenAnswer {
            (it.arguments[0] as AnalysisCase).apply { id = 11L }
        }

        val result = service.create(
            userId = 7L,
            command = AnalysisCaseCreateCommand(AnalysisStage.BEFORE_CONTRACT, 3L),
        )

        assertThat(result.id).isEqualTo(11L)
        assertThat(result.templateVersion).isEqualTo(ANALYSIS_TEMPLATE_VERSION)
    }

    @Test
    fun `hides a property not owned by the user`() {
        `when`(propertyRepository.findByIdAndUserId(3L, 7L)).thenReturn(null)

        assertThatThrownBy {
            service.create(7L, AnalysisCaseCreateCommand(AnalysisStage.BEFORE_CONTRACT, 3L))
        }.isInstanceOf(HomePropertyNotFoundException::class.java)
    }

    @Test
    fun `returns six empty document slots and saved answers`() {
        `when`(caseRepository.findByIdAndUserId(11L, 7L)).thenReturn(analysisCase())
        `when`(documentRepository.findAllByCaseId(11L)).thenReturn(emptyList())
        `when`(answerRepository.findAllByCaseId(11L)).thenReturn(emptyList())

        val result = service.get(7L, 11L)

        assertThat(result.documents).hasSize(6)
        assertThat(result.uploadedCount).isZero()
        assertThat(result.answers).isEmpty()
    }

    private fun property(): HomeProperty =
        HomeProperty(
            id = 3L,
            userId = 7L,
            address = "서울시 마포구 합정동 123-45",
            depositAmount = 25000L,
            buildingType = BuildingType.MULTI_FAMILY,
            landlordName = "홍길동",
            plannedContractDate = LocalDate.parse("2026-08-01"),
        )

    private fun analysisCase(): AnalysisCase =
        AnalysisCase(
            id = 11L,
            userId = 7L,
            propertyId = 3L,
            stage = AnalysisStage.BEFORE_CONTRACT,
            templateVersion = ANALYSIS_TEMPLATE_VERSION,
        )
}
