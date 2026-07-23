// 분석 케이스 생성과 입력 상태 조회를 처리하는 서비스
package com.safelense.analysis

import com.safelense.property.HomePropertyNotFoundException
import com.safelense.property.HomePropertyRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

data class AnalysisCaseCreateCommand(
    val stage: AnalysisStage,
    val propertyId: Long,
)

data class AnalysisCaseCreated(
    val id: Long,
    val propertyId: Long,
    val stage: AnalysisStage,
    val templateVersion: String,
)

data class AnalysisDocumentSlotView(
    val documentType: String,
    val label: String,
    val required: Boolean,
    val documentId: Long?,
    val originalFileName: String?,
    val mimeType: String?,
    val fileSize: Long?,
)

data class AnalysisChecklistAnswerView(
    val itemKey: String,
    val checked: Boolean,
)

data class AnalysisCaseView(
    val id: Long,
    val propertyId: Long,
    val stage: AnalysisStage,
    val templateVersion: String,
    val documents: List<AnalysisDocumentSlotView>,
    val uploadedCount: Int,
    val answers: List<AnalysisChecklistAnswerView>,
)

@Service
class AnalysisCaseService(
    private val propertyRepository: HomePropertyRepository,
    private val caseRepository: AnalysisCaseRepository,
    private val documentRepository: AnalysisDocumentRepository,
    private val answerRepository: AnalysisChecklistAnswerRepository,
    private val catalog: AnalysisTemplateCatalog,
) {
    @Transactional
    fun create(userId: Long, command: AnalysisCaseCreateCommand): AnalysisCaseCreated {
        propertyRepository.findByIdAndUserId(command.propertyId, userId)
            ?: throw HomePropertyNotFoundException()
        val template = catalog.get(command.stage)
        val saved = caseRepository.save(
            AnalysisCase(
                userId = userId,
                propertyId = command.propertyId,
                stage = command.stage,
                templateVersion = template.version,
            ),
        )
        return AnalysisCaseCreated(
            id = requireNotNull(saved.id),
            propertyId = saved.propertyId,
            stage = saved.stage,
            templateVersion = saved.templateVersion,
        )
    }

    @Transactional(readOnly = true)
    fun get(userId: Long, caseId: Long): AnalysisCaseView {
        val analysisCase = caseRepository.findByIdAndUserId(caseId, userId)
            ?: throw AnalysisCaseNotFoundException()
        val template = catalog.get(analysisCase.stage)
        val documents = documentRepository.findAllByCaseId(caseId).associateBy { it.documentType }
        val answers = answerRepository.findAllByCaseId(caseId).associateBy { it.itemKey }
        return AnalysisCaseView(
            id = requireNotNull(analysisCase.id),
            propertyId = analysisCase.propertyId,
            stage = analysisCase.stage,
            templateVersion = analysisCase.templateVersion,
            documents = template.documents.map { slot ->
                val document = documents[slot.documentType]
                AnalysisDocumentSlotView(
                    documentType = slot.documentType,
                    label = slot.label,
                    required = slot.required,
                    documentId = document?.id,
                    originalFileName = document?.originalFileName,
                    mimeType = document?.mimeType,
                    fileSize = document?.fileSize,
                )
            },
            uploadedCount = documents.size,
            answers = catalog.itemKeys(analysisCase.stage).mapNotNull { itemKey ->
                answers[itemKey]?.let { AnalysisChecklistAnswerView(it.itemKey, it.checked) }
            },
        )
    }
}
