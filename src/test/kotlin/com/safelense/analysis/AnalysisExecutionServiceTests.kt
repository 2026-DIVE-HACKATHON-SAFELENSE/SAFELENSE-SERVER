// 분석 실행의 사용자 격리·멱등성·입력 스냅샷 저장을 검증하는 테스트
package com.safelense.analysis

import com.safelense.property.BuildingType
import com.safelense.property.HomeProperty
import com.safelense.property.HomePropertyRepository
import java.time.Instant
import java.time.LocalDate
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import tools.jackson.databind.ObjectMapper

class AnalysisExecutionServiceTests {
    private val caseRepository = mock(AnalysisCaseRepository::class.java)
    private val propertyRepository = mock(HomePropertyRepository::class.java)
    private val documentRepository = mock(AnalysisDocumentRepository::class.java)
    private val answerRepository = mock(AnalysisChecklistAnswerRepository::class.java)
    private val resultRepository = mock(AnalysisResultRepository::class.java)
    private val objectMapper = mock(ObjectMapper::class.java)
    private val service = AnalysisExecutionService(
        caseRepository,
        propertyRepository,
        documentRepository,
        answerRepository,
        resultRepository,
        AnalysisRiskRuleEngine(),
        objectMapper,
    )

    @Test
    fun `creates and stores an analysis result with an input snapshot`() {
        givenOwnedCase()
        `when`(resultRepository.findByCaseId(11L)).thenReturn(null)
        `when`(documentRepository.findAllMetadataByCaseId(11L)).thenReturn(
            listOf(document("REGISTRY_CERTIFICATE")),
        )
        `when`(answerRepository.findAllByCaseId(11L)).thenReturn(
            listOf(answer("CONFIRMED_OWNER", true)),
        )
        `when`(objectMapper.writeValueAsString(any(AnalysisInputSnapshot::class.java)))
            .thenReturn("""{"caseId":11}""")
        `when`(resultRepository.save(any(AnalysisResult::class.java))).thenAnswer {
            (it.arguments[0] as AnalysisResult).apply { id = 31L }
        }

        val outcome = service.analyze(7L, 11L, "request-1", completeCommand())

        assertThat(outcome.created).isTrue()
        assertThat(outcome.result.id).isEqualTo(31L)
        assertThat(outcome.result.ruleVersion).isEqualTo(ANALYSIS_RULE_VERSION)

        val captor = ArgumentCaptor.forClass(AnalysisResult::class.java)
        verify(resultRepository).save(captor.capture())
        assertThat(captor.value.userId).isEqualTo(7L)
        assertThat(captor.value.caseId).isEqualTo(11L)
        assertThat(captor.value.idempotencyKey).isEqualTo("request-1")
        assertThat(captor.value.inputSnapshot).isEqualTo("""{"caseId":11}""")
    }

    @Test
    fun `returns the stored result for the same idempotency key without reading inputs`() {
        `when`(caseRepository.findByIdAndUserIdForUpdate(11L, 7L)).thenReturn(analysisCase())
        `when`(resultRepository.findByCaseId(11L)).thenReturn(result("request-1"))

        val outcome = service.analyze(7L, 11L, "request-1", completeCommand())

        assertThat(outcome.created).isFalse()
        assertThat(outcome.result.id).isEqualTo(31L)
        verify(propertyRepository, never()).findByIdAndUserId(anyLong(), anyLong())
        verify(documentRepository, never()).findAllMetadataByCaseId(anyLong())
        verify(answerRepository, never()).findAllByCaseId(anyLong())
        verify(resultRepository, never()).save(any(AnalysisResult::class.java))
    }

    @Test
    fun `rejects a different idempotency key for a completed case`() {
        `when`(caseRepository.findByIdAndUserIdForUpdate(11L, 7L)).thenReturn(analysisCase())
        `when`(resultRepository.findByCaseId(11L)).thenReturn(result("request-1"))

        assertThatThrownBy {
            service.analyze(7L, 11L, "request-2", completeCommand())
        }.isInstanceOf(AnalysisAlreadyCompletedException::class.java)

        verify(propertyRepository, never()).findByIdAndUserId(anyLong(), anyLong())
    }

    @Test
    fun `hides an analysis case not owned by the user`() {
        `when`(caseRepository.findByIdAndUserIdForUpdate(11L, 7L)).thenReturn(null)

        assertThatThrownBy {
            service.analyze(7L, 11L, "request-1", completeCommand())
        }.isInstanceOf(AnalysisCaseNotFoundException::class.java)

        verify(resultRepository, never()).findByCaseId(anyLong())
    }

    @Test
    fun `rejects invalid keys and monetary facts before reading the case`() {
        val calls: List<() -> Unit> = listOf(
            { service.analyze(7L, 11L, "", completeCommand()) },
            { service.analyze(7L, 11L, "x".repeat(101), completeCommand()) },
            {
                service.analyze(
                    7L,
                    11L,
                    "request-1",
                    completeCommand().copy(estimatedPropertyValueManwon = 0L),
                )
            },
            {
                service.analyze(
                    7L,
                    11L,
                    "request-1",
                    completeCommand().copy(seniorClaimAmountManwon = -1L),
                )
            },
        )

        calls.forEach { call ->
            assertThatThrownBy(call)
                .isInstanceOf(InvalidAnalysisExecutionRequestException::class.java)
        }
        verifyNoInteractions(caseRepository)
    }

    private fun givenOwnedCase() {
        `when`(caseRepository.findByIdAndUserIdForUpdate(11L, 7L)).thenReturn(analysisCase())
        `when`(propertyRepository.findByIdAndUserId(3L, 7L)).thenReturn(property())
    }

    private fun completeCommand() =
        AnalysisExecutionCommand(
            estimatedPropertyValueManwon = 30_000L,
            seniorClaimAmountManwon = 0L,
            seniorRightStatus = SeniorRightStatus.NONE,
            depositGuaranteeStatus = DepositGuaranteeStatus.ENROLLED,
            ownershipStatus = OwnershipStatus.MATCHED,
            seizureOrAuctionStatus = SeizureOrAuctionStatus.NONE,
        )

    private fun analysisCase() =
        AnalysisCase(
            id = 11L,
            userId = 7L,
            propertyId = 3L,
            stage = AnalysisStage.BEFORE_CONTRACT,
            templateVersion = ANALYSIS_TEMPLATE_VERSION,
        )

    private fun property() =
        HomeProperty(
            id = 3L,
            userId = 7L,
            address = "서울시 마포구 합정동 123-45",
            depositAmount = 25_000L,
            buildingType = BuildingType.MULTI_FAMILY,
            landlordName = "홍길동",
            plannedContractDate = LocalDate.parse("2026-08-01"),
        )

    private fun document(documentType: String) =
        AnalysisDocumentMetadata(
            id = 101L,
            documentType = documentType,
            originalFileName = "서류.pdf",
            mimeType = "application/pdf",
            fileSize = 1024L,
        )

    private fun answer(itemKey: String, checked: Boolean) =
        AnalysisChecklistAnswer(
            id = 201L,
            caseId = 11L,
            itemKey = itemKey,
            checked = checked,
        )

    private fun result(idempotencyKey: String) =
        AnalysisResult(
            id = 31L,
            caseId = 11L,
            userId = 7L,
            propertyId = 3L,
            stage = AnalysisStage.BEFORE_CONTRACT,
            score = 40,
            grade = AnalysisRiskGrade.MEDIUM,
            confidence = 100,
            summary = "확인이 필요한 위험 신호가 있습니다.",
            findings = "유효 담보비율은 83.3%입니다.",
            recommendations = "등기부등본을 확인하세요.",
            ruleVersion = ANALYSIS_RULE_VERSION,
            idempotencyKey = idempotencyKey,
            inputSnapshot = """{"caseId":11}""",
            analyzedAt = Instant.parse("2026-07-24T10:15:30Z"),
        )
}
