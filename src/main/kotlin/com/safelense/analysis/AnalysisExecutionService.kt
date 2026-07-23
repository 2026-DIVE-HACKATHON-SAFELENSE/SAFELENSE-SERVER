// 분석 케이스 입력을 조립해 멱등하게 위험 분석 결과를 생성하는 서비스
package com.safelense.analysis

import com.safelense.property.HomePropertyRepository
import java.time.Instant
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper

data class AnalysisExecutionCommand(
    val estimatedPropertyValueManwon: Long? = null,
    val seniorClaimAmountManwon: Long? = null,
    val seniorRightStatus: SeniorRightStatus = SeniorRightStatus.UNKNOWN,
    val depositGuaranteeStatus: DepositGuaranteeStatus = DepositGuaranteeStatus.UNKNOWN,
    val ownershipStatus: OwnershipStatus = OwnershipStatus.UNKNOWN,
    val seizureOrAuctionStatus: SeizureOrAuctionStatus = SeizureOrAuctionStatus.UNKNOWN,
)

data class AnalysisInputSnapshot(
    val caseId: Long,
    val propertyId: Long,
    val stage: AnalysisStage,
    val templateVersion: String,
    val address: String,
    val depositAmountManwon: Long,
    val buildingType: String,
    val documentTypes: List<String>,
    val checklistAnswers: Map<String, Boolean>,
    val riskFacts: AnalysisExecutionCommand,
)

data class AnalysisExecutionOutcome(
    val result: AnalysisResultDetail,
    val created: Boolean,
)

@Service
class AnalysisExecutionService(
    private val caseRepository: AnalysisCaseRepository,
    private val propertyRepository: HomePropertyRepository,
    private val documentRepository: AnalysisDocumentRepository,
    private val answerRepository: AnalysisChecklistAnswerRepository,
    private val resultRepository: AnalysisResultRepository,
    private val ruleEngine: AnalysisRiskRuleEngine,
    private val objectMapper: ObjectMapper,
) {
    @Transactional
    fun analyze(
        userId: Long,
        caseId: Long,
        idempotencyKey: String,
        command: AnalysisExecutionCommand,
    ): AnalysisExecutionOutcome {
        validate(idempotencyKey, command)
        val analysisCase = caseRepository.findByIdAndUserIdForUpdate(caseId, userId)
            ?: throw AnalysisCaseNotFoundException()
        resultRepository.findByCaseId(caseId)?.let { existing ->
            if (existing.idempotencyKey != idempotencyKey) {
                throw AnalysisAlreadyCompletedException()
            }
            return AnalysisExecutionOutcome(existing.toDetail(), false)
        }

        val property = propertyRepository.findByIdAndUserId(analysisCase.propertyId, userId)
            ?: throw AnalysisCaseNotFoundException()
        val documents = documentRepository.findAllMetadataByCaseId(caseId)
        val answers = answerRepository.findAllByCaseId(caseId)
        val answerMap = answers.associate { it.itemKey to it.checked }
        val assessment = ruleEngine.assess(
            AnalysisRiskInput(
                stage = analysisCase.stage,
                depositAmountManwon = property.depositAmount,
                estimatedPropertyValueManwon = command.estimatedPropertyValueManwon,
                seniorClaimAmountManwon = command.seniorClaimAmountManwon,
                seniorRightStatus = command.seniorRightStatus,
                depositGuaranteeStatus = command.depositGuaranteeStatus,
                ownershipStatus = command.ownershipStatus,
                seizureOrAuctionStatus = command.seizureOrAuctionStatus,
                checklistAnswers = answerMap,
            ),
        )
        val snapshot = objectMapper.writeValueAsString(
            AnalysisInputSnapshot(
                caseId = caseId,
                propertyId = property.id ?: analysisCase.propertyId,
                stage = analysisCase.stage,
                templateVersion = analysisCase.templateVersion,
                address = property.address,
                depositAmountManwon = property.depositAmount,
                buildingType = property.buildingType.name,
                documentTypes = documents.map { it.documentType }.sorted(),
                checklistAnswers = answerMap.toSortedMap(),
                riskFacts = command,
            ),
        )
        val saved = resultRepository.save(
            AnalysisResult(
                caseId = caseId,
                userId = userId,
                propertyId = analysisCase.propertyId,
                stage = analysisCase.stage,
                score = assessment.score,
                grade = assessment.grade,
                confidence = assessment.confidence,
                summary = assessment.summary,
                findings = assessment.findings.joinToString("\n"),
                recommendations = assessment.recommendations.joinToString("\n"),
                ruleVersion = assessment.ruleVersion,
                idempotencyKey = idempotencyKey,
                inputSnapshot = snapshot,
                analyzedAt = Instant.now(),
            ),
        )
        return AnalysisExecutionOutcome(saved.toDetail(), true)
    }

    private fun validate(
        idempotencyKey: String,
        command: AnalysisExecutionCommand,
    ) {
        if (
            idempotencyKey.isBlank() ||
            idempotencyKey.length > 100 ||
            command.estimatedPropertyValueManwon?.let { it <= 0 } == true ||
            command.seniorClaimAmountManwon?.let { it < 0 } == true ||
            (
                command.seniorRightStatus == SeniorRightStatus.NONE &&
                    command.seniorClaimAmountManwon?.let { it > 0 } == true
            )
        ) {
            throw InvalidAnalysisExecutionRequestException()
        }
    }
}
