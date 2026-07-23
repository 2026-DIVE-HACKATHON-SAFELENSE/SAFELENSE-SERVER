// 분석 체크리스트 전체 교체 규칙을 검증하는 서비스 테스트
package com.safelense.analysis

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyList
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`

class AnalysisChecklistServiceTests {
    private val caseRepository = mock(AnalysisCaseRepository::class.java)
    private val answerRepository = mock(AnalysisChecklistAnswerRepository::class.java)
    private val service = AnalysisChecklistService(
        caseRepository,
        answerRepository,
        AnalysisTemplateCatalog(),
    )

    @Test
    fun `replaces all answers in template order`() {
        `when`(caseRepository.findByIdAndUserIdForUpdate(11L, 7L)).thenReturn(analysisCase())
        `when`(answerRepository.saveAll(anyList<AnalysisChecklistAnswer>())).thenAnswer {
            @Suppress("UNCHECKED_CAST")
            it.arguments[0] as List<AnalysisChecklistAnswer>
        }

        val result = service.replace(
            userId = 7L,
            caseId = 11L,
            answers = listOf(
                AnalysisChecklistAnswerCommand("CHECKED_INTERIOR", false),
                AnalysisChecklistAnswerCommand("VISITED_PROPERTY", true),
            ),
        )

        verify(answerRepository).deleteAllByCaseId(11L)
        verify(answerRepository).saveAll(anyList<AnalysisChecklistAnswer>())
        assertThat(result)
            .containsExactly(
                AnalysisChecklistAnswerView("VISITED_PROPERTY", true),
                AnalysisChecklistAnswerView("CHECKED_INTERIOR", false),
            )
    }

    @Test
    fun `rejects unknown and duplicate item keys before replacing answers`() {
        `when`(caseRepository.findByIdAndUserIdForUpdate(11L, 7L)).thenReturn(analysisCase())

        assertThatThrownBy {
            service.replace(7L, 11L, listOf(AnalysisChecklistAnswerCommand("UNKNOWN", true)))
        }.isInstanceOf(InvalidAnalysisChecklistException::class.java)

        assertThatThrownBy {
            service.replace(
                7L,
                11L,
                listOf(
                    AnalysisChecklistAnswerCommand("VISITED_PROPERTY", true),
                    AnalysisChecklistAnswerCommand("VISITED_PROPERTY", false),
                ),
            )
        }.isInstanceOf(InvalidAnalysisChecklistException::class.java)

        verify(answerRepository, never()).deleteAllByCaseId(11L)
        verify(answerRepository, never()).saveAll(anyList<AnalysisChecklistAnswer>())
    }

    @Test
    fun `replaces the same item key twice after deleting the previous answer`() {
        `when`(caseRepository.findByIdAndUserIdForUpdate(11L, 7L)).thenReturn(analysisCase())
        `when`(answerRepository.saveAll(anyList<AnalysisChecklistAnswer>())).thenAnswer {
            @Suppress("UNCHECKED_CAST")
            it.arguments[0] as List<AnalysisChecklistAnswer>
        }

        service.replace(
            7L,
            11L,
            listOf(AnalysisChecklistAnswerCommand("VISITED_PROPERTY", true)),
        )
        val replaced = service.replace(
            7L,
            11L,
            listOf(AnalysisChecklistAnswerCommand("VISITED_PROPERTY", false)),
        )

        val order = inOrder(answerRepository)
        order.verify(answerRepository).deleteAllByCaseId(11L)
        order.verify(answerRepository).saveAll(anyList<AnalysisChecklistAnswer>())
        order.verify(answerRepository).deleteAllByCaseId(11L)
        order.verify(answerRepository).saveAll(anyList<AnalysisChecklistAnswer>())
        assertThat(replaced).containsExactly(AnalysisChecklistAnswerView("VISITED_PROPERTY", false))
    }

    @Test
    fun `deletes existing answers without saving when replacement is empty`() {
        `when`(caseRepository.findByIdAndUserIdForUpdate(11L, 7L)).thenReturn(analysisCase())

        val result = service.replace(7L, 11L, emptyList())

        assertThat(result).isEmpty()
        verify(answerRepository).deleteAllByCaseId(11L)
        verify(answerRepository, never()).saveAll(anyList<AnalysisChecklistAnswer>())
    }

    @Test
    fun `hides a checklist case not owned by the user`() {
        `when`(caseRepository.findByIdAndUserIdForUpdate(11L, 7L)).thenReturn(null)

        assertThatThrownBy {
            service.replace(
                7L,
                11L,
                listOf(AnalysisChecklistAnswerCommand("VISITED_PROPERTY", true)),
            )
        }.isInstanceOf(AnalysisCaseNotFoundException::class.java)

        verifyNoInteractions(answerRepository)
    }

    private fun analysisCase(): AnalysisCase =
        AnalysisCase(
            id = 11L,
            userId = 7L,
            propertyId = 3L,
            stage = AnalysisStage.BEFORE_CONTRACT,
            templateVersion = ANALYSIS_TEMPLATE_VERSION,
        )
}
