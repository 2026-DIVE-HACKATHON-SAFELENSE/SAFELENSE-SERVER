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
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Clock
import java.time.Instant
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper

const val ANALYSIS_PROMPT_VERSION = "contract-report-2026-v1"

@Schema(description = "계약 안전성 분석")
data class ContractSafetyReport(
    @field:Schema(description = "위험 신호 점수. 근거가 부족하면 null", example = "72")
    var score: Int? = null,
    @field:Schema(description = "위험 등급", example = "HIGH")
    var grade: AnalysisRiskGrade = AnalysisRiskGrade.UNKNOWN,
    @field:Schema(description = "수집 근거 충족률. 0부터 100 사이", example = "80")
    var confidence: Int = 0,
    @field:Schema(description = "규칙 기반 계약 안전성 요약")
    var summary: String = "",
    @field:Schema(description = "수집 근거를 인용하는 주요 위험 신호")
    var findings: List<EvidenceBackedStatement> = emptyList(),
)

@Schema(description = "검증된 AI 종합 해석")
data class AiInterpretationReport(
    @field:Schema(description = "수집 근거를 인용하는 AI 종합 해석")
    var summary: EvidenceBackedStatement = EvidenceBackedStatement(),
    @field:Schema(description = "AI 결과 대신 규칙 기반 문구를 사용했는지 여부", example = "false")
    var fallback: Boolean = false,
)

@Schema(description = "리포트 데이터 수집 범위")
data class DataCoverageItem(
    @field:Schema(description = "수집 근거 종류", example = "OFFICIAL_PRICE")
    var evidenceKey: String = "",
    @field:Schema(description = "근거 가용성과 품질 상태", example = "AVAILABLE")
    var status: EvidenceStatus = EvidenceStatus.NOT_AVAILABLE,
    @field:Schema(description = "데이터 출처", example = "SAFELENSE_DEMO")
    var source: String = "",
    @field:Schema(description = "데이터 기준일. 기준일이 없으면 null", example = "2026-07-26T00:00:00Z")
    var asOf: String? = null,
)

@Schema(description = "실제 비식별 상담 데이터에서 검색한 유사 사례")
data class SimilarCaseReport(
    @field:Schema(description = "상담 데이터셋의 비식별 사례 ID")
    var caseId: String = "",
    @field:Schema(description = "구조화 조건과 의미 검색의 결합 유사도", example = "0.845")
    var similarity: Double = 0.0,
    @field:Schema(description = "일반화된 분쟁 유형과 진행 단계")
    var pattern: String = "",
    @field:Schema(description = "원문을 노출하지 않는 일반화 사례 설명")
    var summary: String = "",
)

@Schema(description = "근거 기반 계약 의사결정 리포트")
data class ContractDecisionReportView(
    @field:Schema(description = "계약 안전성 점수와 위험 신호")
    var contractSafety: ContractSafetyReport = ContractSafetyReport(),
    @field:Schema(description = "거주 환경에 영향을 줄 수 있는 근거 기반 문장")
    var residentialImpacts: List<EvidenceBackedStatement> = emptyList(),
    @field:Schema(description = "검증된 AI 종합 해석")
    var aiInterpretation: AiInterpretationReport = AiInterpretationReport(),
    @field:Schema(description = "계약 전 확인할 행동 가이드")
    var actionGuide: List<EvidenceBackedStatement> = emptyList(),
    @field:Schema(description = "실제 비식별 상담 데이터에서 검색한 유사 사례")
    var similarCases: List<SimilarCaseReport> = emptyList(),
    @field:Schema(description = "근거별 수집 상태와 출처")
    var dataCoverage: List<DataCoverageItem> = emptyList(),
    @field:Schema(description = "리포트에 사용된 데이터 모드", example = "DEMO")
    var dataMode: AnalysisDataMode = AnalysisDataMode.DEMO,
    @field:Schema(description = "리포트 근거의 최신 기준 시각", example = "2026-07-26T09:00:00Z")
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
            similarCases = matchedCases.map {
                SimilarCaseReport(
                    caseId = it.caseId,
                    similarity = it.combinedScore,
                    pattern = it.pattern,
                    summary = it.summary,
                )
            },
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
        return find(userId, analysisId) ?: throw AnalysisRunNotFoundException()
    }

    @Transactional(readOnly = true)
    fun find(userId: Long, analysisId: Long): ContractDecisionReportView? {
        runRepository.findByIdAndUserId(analysisId, userId) ?: return null
        val report = reportRepository.findByRunId(analysisId) ?: throw AnalysisRunNotFoundException()
        return decode(report.reportJson)
    }

    private fun decode(reportJson: String): ContractDecisionReportView =
        objectMapper.readValue(reportJson, ContractDecisionReportView::class.java)
}
