// 분석 이력 목록과 상세 조회의 사용자 격리 계약을 검증하는 테스트
package com.safelense.analysis

import java.time.Instant
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.springframework.data.domain.PageRequest

class AnalysisResultServiceTests {
    private val repository = mock(AnalysisResultRepository::class.java)
    private val service = AnalysisResultService(repository)

    @Test
    fun `lists owned analysis results with a next cursor`() {
        `when`(
            repository.findByUserIdWithCursor(
                7L,
                null,
                null,
                PageRequest.of(0, 3),
            ),
        ).thenReturn(listOf(result(31L), result(30L), result(29L)))

        val page = service.list(7L, null, 2, null)

        assertThat(page.analyses.map { it.id }).containsExactly(31L, 30L)
        assertThat(page.nextCursor).isEqualTo(30L)
        assertThat(page.hasNext).isTrue()
        assertThat(page.analyses[0].grade).isEqualTo(AnalysisRiskGrade.MEDIUM)
    }

    @Test
    fun `passes the cursor and stage filter and returns the final page`() {
        `when`(
            repository.findByUserIdWithCursor(
                7L,
                31L,
                AnalysisStage.BEFORE_CONTRACT,
                PageRequest.of(0, 21),
            ),
        ).thenReturn(listOf(result(30L)))

        val page = service.list(7L, 31L, 20, AnalysisStage.BEFORE_CONTRACT)

        assertThat(page.analyses.map { it.id }).containsExactly(30L)
        assertThat(page.nextCursor).isNull()
        assertThat(page.hasNext).isFalse()
    }

    @Test
    fun `rejects invalid cursor and size without querying`() {
        val calls: List<() -> Unit> = listOf(
            { service.list(7L, 0L, 20, null) },
            { service.list(7L, -1L, 20, null) },
            { service.list(7L, null, 0, null) },
            { service.list(7L, null, 101, null) },
        )
        calls.forEach { call ->
            assertThatThrownBy(call)
                .isInstanceOf(InvalidAnalysisResultRequestException::class.java)
        }
        verifyNoInteractions(repository)
    }

    @Test
    fun `returns an owned result detail with structured findings`() {
        `when`(repository.findByIdAndUserId(31L, 7L)).thenReturn(
            result(
                31L,
                findings = "위험 근거 1\n\n위험 근거 2",
                recommendations = "권고 1\n권고 2",
            ),
        )

        val detail = service.get(7L, 31L)

        assertThat(detail.id).isEqualTo(31L)
        assertThat(detail.findings).containsExactly("위험 근거 1", "위험 근거 2")
        assertThat(detail.recommendations).containsExactly("권고 1", "권고 2")
        assertThat(detail.ruleVersion).isEqualTo("2026-07-24-v1")
    }

    @Test
    fun `hides a result not owned by the user`() {
        `when`(repository.findByIdAndUserId(31L, 7L)).thenReturn(null)

        assertThatThrownBy { service.get(7L, 31L) }
            .isInstanceOf(AnalysisResultNotFoundException::class.java)
    }

    private fun result(
        id: Long,
        findings: String = "위험 근거",
        recommendations: String = "권고",
    ): AnalysisResult =
        AnalysisResult(
            id = id,
            caseId = 11L,
            userId = 7L,
            propertyId = 5L,
            stage = AnalysisStage.BEFORE_CONTRACT,
            score = 45,
            grade = AnalysisRiskGrade.MEDIUM,
            confidence = 70,
            summary = "확인이 필요한 위험 신호가 있습니다.",
            findings = findings,
            recommendations = recommendations,
            ruleVersion = "2026-07-24-v1",
            analyzedAt = Instant.parse("2026-07-24T10:15:30Z"),
        )
}
