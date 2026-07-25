// 실제 분석 워커와 리포트 저장 흐름의 부분 완료·불변성·민감 정보 비노출을 검증하는 테스트
package com.safelense.analysis

import com.safelense.analysis.collection.PropertyDataCollector
import com.safelense.analysis.evidence.CollectedEvidence
import com.safelense.analysis.evidence.CollectedEvidenceRepository
import com.safelense.analysis.extraction.RegistryDocumentStatusExtractor
import com.safelense.analysis.interpretation.OpenAiProperties
import com.safelense.analysis.interpretation.OpenAiReportClient
import com.safelense.analysis.interpretation.OpenAiReportInterpreter
import com.safelense.analysis.interpretation.UpstageProperties
import com.safelense.analysis.interpretation.ReportEvidenceValidator
import com.safelense.analysis.match.AnalysisCaseMatchRepository
import com.safelense.analysis.match.ConsultationCaseMatcher
import com.safelense.analysis.match.ConsultationMatchResult
import com.safelense.analysis.report.AnalysisReport
import com.safelense.analysis.report.AnalysisReportRepository
import com.safelense.analysis.report.ContractDecisionReportService
import com.safelense.analysis.run.AnalysisDataMode
import com.safelense.analysis.run.AnalysisRun
import com.safelense.analysis.run.AnalysisRunRepository
import com.safelense.analysis.run.AnalysisRunStatus
import com.safelense.analysis.run.AnalysisRunWorker
import com.safelense.document.RegistryDocumentRepository
import com.safelense.property.BuildingType
import com.safelense.property.HomeProperty
import com.safelense.property.HomePropertyRepository
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyList
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import tools.jackson.databind.ObjectMapper

@ExtendWith(OutputCaptureExtension::class)
class ContractDecisionEndToEndTests {
    @Test
    fun `keeps partial historical reports immutable without logging sensitive input`(output: CapturedOutput) {
        val runRepository = mock(AnalysisRunRepository::class.java)
        val propertyRepository = mock(HomePropertyRepository::class.java)
        val documentRepository = mock(RegistryDocumentRepository::class.java)
        val evidenceRepository = mock(CollectedEvidenceRepository::class.java)
        val matchRepository = mock(AnalysisCaseMatchRepository::class.java)
        val reportRepository = mock(AnalysisReportRepository::class.java)
        val property = property()
        val runs = mutableMapOf(
            3L to run(3L, "run-1", forceRefresh = false),
            4L to run(4L, "run-2", forceRefresh = true),
        )
        val reports = mutableMapOf<Long, AnalysisReport>()
        var nextEvidenceId = 11L
        var nextReportId = 21L

        `when`(runRepository.findByIdForUpdate(anyLong())).thenAnswer {
            runs[it.arguments[0] as Long]
        }
        `when`(runRepository.findByIdAndUserId(anyLong(), anyLong())).thenAnswer {
            runs[it.arguments[0] as Long]?.takeIf { run -> run.userId == it.arguments[1] as Long }
        }
        `when`(propertyRepository.findByIdAndUserId(2L, 1L)).thenReturn(property)
        `when`(evidenceRepository.saveAll(anyList<CollectedEvidence>())).thenAnswer {
            @Suppress("UNCHECKED_CAST")
            (it.arguments[0] as List<CollectedEvidence>).onEach { evidence ->
                evidence.id = nextEvidenceId++
            }
        }
        `when`(reportRepository.findByRunId(anyLong())).thenAnswer {
            reports[it.arguments[0] as Long]
        }
        `when`(reportRepository.save(any(AnalysisReport::class.java))).thenAnswer {
            (it.arguments[0] as AnalysisReport).apply { id = nextReportId++ }.also { report ->
                reports[report.runId] = report
            }
        }

        val clock = Clock.fixed(NOW, ZoneOffset.UTC)
        val objectMapper = ObjectMapper()
        val reportService = ContractDecisionReportService(
            reportRepository,
            runRepository,
            OpenAiReportInterpreter(
                OpenAiReportClient {
                    throw IllegalStateException("secret-openai-key at s3://private/${property.address}")
                },
                ReportEvidenceValidator(),
                UpstageProperties("secret-upstage-key", "solar-pro3", "https://api.upstage.ai/v1"),
            ),
            objectMapper,
            clock,
        )
        val worker = AnalysisRunWorker(
            runRepository,
            propertyRepository,
            documentRepository,
            evidenceRepository,
            matchRepository,
            PropertyDataCollector {
                throw IllegalStateException("OPENAI_API_KEY unavailable for ${it.address}")
            },
            RegistryDocumentStatusExtractor(clock),
            ConsultationCaseMatcher { ConsultationMatchResult(emptyList(), degraded = false) },
            AnalysisRiskRuleEngine(),
            objectMapper,
            reportService,
            clock,
        )

        worker.execute(3L)
        val firstReportJson = reports.getValue(3L).reportJson
        property.depositAmount = 30000
        property.address = "서울시 중구 2"
        worker.execute(4L)

        assertThat(runs.getValue(3L).status).isEqualTo(AnalysisRunStatus.PARTIAL)
        assertThat(runs.getValue(4L).status).isEqualTo(AnalysisRunStatus.PARTIAL)
        assertThat(reports.getValue(3L).reportJson).isEqualTo(firstReportJson)
        assertThat(reportService.get(1L, 3L).dataMode).isEqualTo(AnalysisDataMode.DEMO)
        assertThat(output.all).doesNotContain(
            "OPENAI_API_KEY",
            "secret-openai-key",
            "s3://",
            "서울시 중구 1",
            "서울시 중구 2",
        )
    }

    private fun run(id: Long, key: String, forceRefresh: Boolean) =
        AnalysisRun(
            id = id,
            propertyId = 2L,
            userId = 1L,
            status = AnalysisRunStatus.QUEUED,
            dataMode = AnalysisDataMode.DEMO,
            idempotencyKey = key,
            forceRefresh = forceRefresh,
        )

    private fun property() =
        HomeProperty(
            id = 2L,
            userId = 1L,
            address = "서울시 중구 1",
            depositAmount = 20000,
            buildingType = BuildingType.APARTMENT,
        )

    companion object {
        private val NOW = Instant.parse("2026-07-26T00:00:00Z")
    }
}
