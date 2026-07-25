// 규칙 결과와 검증된 AI 해석을 실행별 불변 계약 의사결정 리포트로 저장하는 서비스
package com.safelense.analysis.report

import com.safelense.analysis.AnalysisRiskAssessment
import com.safelense.analysis.AnalysisRiskGrade
import com.safelense.analysis.evidence.CollectedEvidence
import com.safelense.analysis.evidence.EvidenceStatus
import com.safelense.analysis.interpretation.EvidenceBackedStatement
import com.safelense.analysis.interpretation.OpenAiReportInterpreter
import com.safelense.analysis.match.MatchedCase
import com.safelense.analysis.run.AnalysisDataMode
import com.safelense.analysis.run.AnalysisRun
import com.safelense.analysis.run.AnalysisRunNotFoundException
import com.safelense.analysis.run.AnalysisRunRepository
import java.time.Clock
import java.time.Instant
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper

const val ANALYSIS_PROMPT_VERSION = "contract-report-2026-v1"

data class ContractSafetyReport(
    var score: Int? = null,
    var grade: AnalysisRiskGrade = AnalysisRiskGrade.UNKNOWN,
    var confidence: Int = 0,
    var summary: String = "",
    var findings: List<EvidenceBackedStatement> = emptyList(),
)

data class AiInterpretationReport(
    var summary: EvidenceBackedStatement = EvidenceBackedStatement(),
    var fallback: Boolean = false,
)

data class DataCoverageItem(
    var evidenceKey: String = "",
    var status: EvidenceStatus = EvidenceStatus.NOT_AVAILABLE,
    var source: String = "",
    var asOf: String? = null,
)

data class ContractDecisionReportView(
    var contractSafety: ContractSafetyReport = ContractSafetyReport(),
    var residentialImpacts: List<EvidenceBackedStatement> = emptyList(),
    var aiInterpretation: AiInterpretationReport = AiInterpretationReport(),
    var actionGuide: List<EvidenceBackedStatement> = emptyList(),
    var dataCoverage: List<DataCoverageItem> = emptyList(),
    var dataMode: AnalysisDataMode = AnalysisDataMode.DEMO,
    var asOf: String = Instant.EPOCH.toString(),
)

data class ContractDecisionReportGeneration(
    val view: ContractDecisionReportView,
    val created: Boolean,
)

fun interface ContractDecisionReportGenerator {
    fun generate(
        run: AnalysisRun,
        evidence: List<CollectedEvidence>,
        matchedCases: List<MatchedCase>,
        assessment: AnalysisRiskAssessment,
    ): ContractDecisionReportGeneration
}

@Service
class ContractDecisionReportService(
    private val reportRepository: AnalysisReportRepository,
    private val runRepository: AnalysisRunRepository,
    private val interpreter: OpenAiReportInterpreter,
    private val objectMapper: ObjectMapper,
    private val clock: Clock = Clock.systemUTC(),
) : ContractDecisionReportGenerator {
    @Transactional
    override fun generate(
        run: AnalysisRun,
        evidence: List<CollectedEvidence>,
        matchedCases: List<MatchedCase>,
        assessment: AnalysisRiskAssessment,
    ): ContractDecisionReportGeneration {
        val runId = requireNotNull(run.id)
        reportRepository.findByRunId(runId)?.let {
            return ContractDecisionReportGeneration(decode(it.reportJson), false)
        }
        val interpreted = interpreter.interpret(evidence, assessment, matchedCases)
        val availableIds = evidence
            .filter { it.status == EvidenceStatus.AVAILABLE }
            .map { "evidence-${requireNotNull(it.id)}" }
        val view = ContractDecisionReportView(
            contractSafety = ContractSafetyReport(
                score = assessment.score,
                grade = assessment.grade,
                confidence = assessment.confidence,
                summary = assessment.summary,
                findings = assessment.findings.map {
                    EvidenceBackedStatement(it, availableIds)
                },
            ),
            residentialImpacts = interpreted.result.residentialImpacts,
            aiInterpretation = AiInterpretationReport(
                summary = interpreted.result.summary,
                fallback = interpreted.fallback,
            ),
            actionGuide = interpreted.result.actionGuide,
            dataCoverage = evidence.map {
                DataCoverageItem(it.evidenceKey, it.status, it.source, it.asOf?.toString())
            },
            dataMode = run.dataMode,
            asOf = evidence.mapNotNull(CollectedEvidence::asOf).maxOrNull()?.toString()
                ?: run.completedAt?.toString()
                ?: Instant.now(clock).toString(),
        )
        reportRepository.save(
            AnalysisReport(
                runId = runId,
                reportJson = objectMapper.writeValueAsString(view),
                ruleVersion = assessment.ruleVersion,
                promptVersion = ANALYSIS_PROMPT_VERSION,
                model = interpreted.model,
            ),
        )
        return ContractDecisionReportGeneration(view, true)
    }

    @Transactional(readOnly = true)
    fun get(userId: Long, analysisId: Long): ContractDecisionReportView {
        runRepository.findByIdAndUserId(analysisId, userId) ?: throw AnalysisRunNotFoundException()
        val report = reportRepository.findByRunId(analysisId) ?: throw AnalysisRunNotFoundException()
        return decode(report.reportJson)
    }

    private fun decode(reportJson: String): ContractDecisionReportView =
        objectMapper.readValue(reportJson, ContractDecisionReportView::class.java)
}
