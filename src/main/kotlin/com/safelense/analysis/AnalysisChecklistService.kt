// 분석 체크리스트 답변을 템플릿 기준으로 전체 교체하는 서비스
package com.safelense.analysis

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

data class AnalysisChecklistAnswerCommand(
    val itemKey: String,
    val checked: Boolean,
)

@Service
class AnalysisChecklistService(
    private val caseRepository: AnalysisCaseRepository,
    private val answerRepository: AnalysisChecklistAnswerRepository,
    private val catalog: AnalysisTemplateCatalog,
) {
    @Transactional
    fun replace(
        userId: Long,
        caseId: Long,
        answers: List<AnalysisChecklistAnswerCommand>,
    ): List<AnalysisChecklistAnswerView> {
        val analysisCase = caseRepository.findByIdAndUserIdForUpdate(caseId, userId)
            ?: throw AnalysisCaseNotFoundException()
        val requestKeys = answers.map { it.itemKey }
        val allowedKeys = catalog.itemKeys(analysisCase.stage)
        if (requestKeys.distinct().size != requestKeys.size || requestKeys.any { it !in allowedKeys }) {
            throw InvalidAnalysisChecklistException()
        }

        answerRepository.deleteAllByCaseId(caseId)
        val savedByKey = if (answers.isEmpty()) {
            emptyMap()
        } else {
            answerRepository.saveAll(
                answers.map {
                    AnalysisChecklistAnswer(
                        caseId = caseId,
                        itemKey = it.itemKey,
                        checked = it.checked,
                    )
                },
            ).associateBy { it.itemKey }
        }
        return allowedKeys.mapNotNull { itemKey ->
            savedByKey[itemKey]?.let { AnalysisChecklistAnswerView(it.itemKey, it.checked) }
        }
    }
}
