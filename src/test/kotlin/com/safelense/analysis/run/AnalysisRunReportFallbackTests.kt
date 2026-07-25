// AI 해석 fallback이 분석 실행을 부분 완료로 전환하는지 검증하는 테스트
package com.safelense.analysis.run

import com.safelense.analysis.AnalysisRiskRuleEngine
import com.safelense.analysis.collection.CollectedEvidenceCommand
import com.safelense.analysis.collection.PropertyDataCollector
import com.safelense.analysis.evidence.CollectedEvidence
import com.safelense.analysis.evidence.CollectedEvidenceRepository
import com.safelense.analysis.evidence.EvidenceStatus
import com.safelense.analysis.extraction.RegistryExtractor
import com.safelense.analysis.match.AnalysisCaseMatchRepository
import com.safelense.analysis.match.ConsultationCaseMatcher
import com.safelense.analysis.match.ConsultationMatchResult
import com.safelense.analysis.report.AiInterpretationReport
import com.safelense.analysis.report.ContractDecisionReportGeneration
import com.safelense.analysis.report.ContractDecisionReportGenerator
import com.safelense.analysis.report.ContractDecisionReportView
import com.safelense.document.RegistryDocumentRepository
import com.safelense.property.BuildingType
import com.safelense.property.HomeProperty
import com.safelense.property.HomePropertyRepository
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyList
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import tools.jackson.databind.ObjectMapper

class AnalysisRunReportFallbackTests {
    @Test
    fun `marks an otherwise complete run partial when report interpretation falls back`() {
        val runRepository = mock(AnalysisRunRepository::class.java)
        val propertyRepository = mock(HomePropertyRepository::class.java)
        val documentRepository = mock(RegistryDocumentRepository::class.java)
        val evidenceRepository = mock(CollectedEvidenceRepository::class.java)
        val matchRepository = mock(AnalysisCaseMatchRepository::class.java)
        val run = AnalysisRun(
            id = 3L,
            propertyId = 2L,
            userId = 1L,
            status = AnalysisRunStatus.QUEUED,
            dataMode = AnalysisDataMode.DEMO,
            idempotencyKey = "run-1",
            forceRefresh = false,
        )
        `when`(runRepository.findByIdForUpdate(3L)).thenReturn(run)
        `when`(propertyRepository.findByIdAndUserId(2L, 1L)).thenReturn(property())
        `when`(documentRepository.findFirstByPropertyIdAndDeletedAtIsNullOrderByIdDesc(2L)).thenReturn(null)
        `when`(evidenceRepository.saveAll(anyList<CollectedEvidence>())).thenAnswer {
            @Suppress("UNCHECKED_CAST")
            (it.arguments[0] as List<CollectedEvidence>).onEachIndexed { index, item -> item.id = index + 1L }
        }
        var reportCalls = 0
        val reportGenerator = ContractDecisionReportGenerator { _, evidence, _, _ ->
            reportCalls += 1
            assertThat(evidence).allMatch { it.id != null }
            ContractDecisionReportGeneration(
                ContractDecisionReportView(
                    aiInterpretation = AiInterpretationReport(fallback = true),
                ),
                created = true,
            )
        }
        val available = listOf(evidence("OFFICIAL_PRICE", """{"amount":50000}"""))
        val worker = AnalysisRunWorker(
            runRepository,
            propertyRepository,
            documentRepository,
            evidenceRepository,
            matchRepository,
            PropertyDataCollector { available },
            RegistryExtractor { listOf(evidence("REGISTRY_DOCUMENT", """{"extracted":true}""")) },
            ConsultationCaseMatcher { ConsultationMatchResult(emptyList(), degraded = false) },
            AnalysisRiskRuleEngine(),
            ObjectMapper(),
            reportGenerator,
            Clock.fixed(NOW, ZoneOffset.UTC),
        )

        worker.execute(3L)

        assertThat(reportCalls).isEqualTo(1)
        assertThat(run.status).isEqualTo(AnalysisRunStatus.PARTIAL)
    }

    private fun evidence(key: String, valueJson: String) =
        CollectedEvidenceCommand(
            evidenceKey = key,
            valueJson = valueJson,
            source = "LIVE_TEST",
            sourceIdentifier = "fixture",
            asOf = NOW,
            collectedAt = NOW,
            confidence = 90,
            status = EvidenceStatus.AVAILABLE,
        )

    private fun property() =
        HomeProperty(
            id = 2L,
            userId = 1L,
            address = "서울특별시 중구 세종대로 110",
            depositAmount = 20000,
            buildingType = BuildingType.APARTMENT,
        )

    companion object {
        private val NOW = Instant.parse("2026-07-26T00:00:00Z")
    }
}
