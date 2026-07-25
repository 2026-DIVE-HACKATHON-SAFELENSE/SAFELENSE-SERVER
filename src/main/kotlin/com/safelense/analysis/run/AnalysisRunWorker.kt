// 대기 중 분석 실행을 잠그고 수집·추출·규칙 분석 상태 전이를 수행하는 워커
package com.safelense.analysis.run

import com.safelense.analysis.AnalysisRiskAssessment
import com.safelense.analysis.AnalysisRiskRuleEngine
import com.safelense.analysis.collection.CollectedEvidenceCommand
import com.safelense.analysis.collection.PropertyDataCollector
import com.safelense.analysis.evidence.CollectedEvidence
import com.safelense.analysis.evidence.CollectedEvidenceRepository
import com.safelense.analysis.evidence.EvidenceStatus
import com.safelense.analysis.extraction.RegistryExtractor
import com.safelense.analysis.match.ConsultationCaseMatcher
import com.safelense.analysis.match.ConsultationMatchRequest
import com.safelense.analysis.match.ConsultationMatchResult
import com.safelense.analysis.match.AnalysisCaseMatch
import com.safelense.analysis.match.AnalysisCaseMatchRepository
import com.safelense.analysis.match.MatchedCase
import com.safelense.analysis.report.ContractDecisionReportGenerator
import com.safelense.document.RegistryDocumentRepository
import com.safelense.property.HomePropertyRepository
import java.time.Clock
import java.time.Instant
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper

data class AnalysisRunArtifacts(
    val evidence: List<CollectedEvidenceCommand>,
    val matchedCases: List<MatchedCase>,
    val assessment: AnalysisRiskAssessment,
)

@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class AnalysisRunWorker(
    private val runRepository: AnalysisRunRepository,
    private val propertyRepository: HomePropertyRepository,
    private val documentRepository: RegistryDocumentRepository,
    private val evidenceRepository: CollectedEvidenceRepository,
    private val matchRepository: AnalysisCaseMatchRepository,
    private val collector: PropertyDataCollector,
    private val extractor: RegistryExtractor,
    private val matcher: ConsultationCaseMatcher,
    private val ruleEngine: AnalysisRiskRuleEngine,
    private val objectMapper: ObjectMapper,
    private val reportGenerator: ContractDecisionReportGenerator,
    private val clock: Clock = Clock.systemUTC(),
) {
    @Scheduled(fixedDelayString = "\${app.analysis.worker-delay:PT5S}")
    @Transactional
    fun poll() {
        runRepository.findFirstByStatusOrderByIdAsc(AnalysisRunStatus.QUEUED)?.let(::executeLocked)
    }

    @Transactional
    fun execute(runId: Long): AnalysisRunArtifacts? =
        runRepository.findByIdForUpdate(runId)?.let(::executeLocked)

    private fun executeLocked(run: AnalysisRun): AnalysisRunArtifacts? {
        if (run.status != AnalysisRunStatus.QUEUED) {
            return null
        }
        val startedAt = Instant.now(clock)
        run.startedAt = startedAt
        return try {
            val property = propertyRepository.findByIdAndUserId(run.propertyId, run.userId)
                ?: error("Owned property is unavailable")
            var providerUnavailable = false

            run.status = AnalysisRunStatus.COLLECTING
            val collected = try {
                collector.collect(property)
            } catch (_: Exception) {
                providerUnavailable = true
                listOf(unavailableEvidence("PROPERTY_DATA_COLLECTION", startedAt))
            }

            run.status = AnalysisRunStatus.EXTRACTING_DOCUMENT
            val document = documentRepository.findFirstByPropertyIdAndDeletedAtIsNullOrderByIdDesc(run.propertyId)
            val extracted = try {
                extractor.extract(document)
            } catch (_: Exception) {
                providerUnavailable = true
                listOf(unavailableEvidence("REGISTRY_DOCUMENT", startedAt))
            }
            val evidence = collected + extracted
            val persistedEvidence = evidenceRepository
                .saveAll(evidence.map { it.toEntity(requireNotNull(run.id)) })
                .toList()

            run.status = AnalysisRunStatus.ANALYZING
            val assessment = ruleEngine.assess(property, evidence, objectMapper)
            val matchResult = try {
                matcher.match(ConsultationMatchRequest(property, evidence, assessment))
            } catch (_: Exception) {
                ConsultationMatchResult(emptyList(), degraded = true)
            }
            providerUnavailable = providerUnavailable || matchResult.degraded
            val matchedCases = matchResult.cases
            matchRepository.saveAll(
                matchedCases.mapIndexed { index, match ->
                    AnalysisCaseMatch(
                        runId = requireNotNull(run.id),
                        consultationCaseId = match.databaseId,
                        rank = index + 1,
                        structuredScore = match.structuredScore,
                        semanticScore = match.semanticScore,
                        combinedScore = match.combinedScore,
                        pattern = match.pattern,
                        summary = match.summary,
                    )
                },
            )
            val report = reportGenerator.generate(run, persistedEvidence, matchedCases, assessment)

            val hasUnavailableEvidence = evidence.any {
                it.status == EvidenceStatus.UNAVAILABLE ||
                    it.status == EvidenceStatus.STALE ||
                    it.status == EvidenceStatus.CONFLICTING
            }
            run.status =
                if (
                    providerUnavailable ||
                    hasUnavailableEvidence ||
                    report.view.aiInterpretation.fallback
                ) AnalysisRunStatus.PARTIAL
                else AnalysisRunStatus.COMPLETED
            run.completedAt = Instant.now(clock)
            AnalysisRunArtifacts(evidence, matchedCases, assessment)
        } catch (_: Exception) {
            run.status = AnalysisRunStatus.FAILED
            run.failureCode = "ANALYSIS_EXECUTION_FAILED"
            run.completedAt = Instant.now(clock)
            null
        }
    }

    private fun unavailableEvidence(evidenceKey: String, collectedAt: Instant) =
        CollectedEvidenceCommand(
            evidenceKey = evidenceKey,
            valueJson = null,
            source = "PUBLIC_DATA_COLLECTION",
            sourceIdentifier = null,
            asOf = null,
            collectedAt = collectedAt,
            confidence = 0,
            status = EvidenceStatus.UNAVAILABLE,
        )

    private fun CollectedEvidenceCommand.toEntity(runId: Long) =
        CollectedEvidence(
            runId = runId,
            evidenceKey = evidenceKey,
            valueJson = valueJson,
            source = source,
            sourceIdentifier = sourceIdentifier,
            asOf = asOf,
            collectedAt = collectedAt,
            confidence = confidence,
            status = status,
        )
}
