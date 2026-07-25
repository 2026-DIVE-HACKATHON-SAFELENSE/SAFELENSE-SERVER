// 분석 워커의 상태 전이·근거 저장·부분 완료 판정을 검증하는 테스트
package com.safelense.analysis.run

import com.safelense.analysis.AnalysisRiskRuleEngine
import com.safelense.analysis.collection.CollectedEvidenceCommand
import com.safelense.analysis.collection.PropertyDataCollector
import com.safelense.analysis.evidence.CollectedEvidence
import com.safelense.analysis.evidence.CollectedEvidenceRepository
import com.safelense.analysis.evidence.EvidenceStatus
import com.safelense.analysis.extraction.RegistryExtractor
import com.safelense.analysis.match.AnalysisCaseMatch
import com.safelense.analysis.match.AnalysisCaseMatchRepository
import com.safelense.analysis.match.ConsultationMatchResult
import com.safelense.analysis.match.ConsultationCaseMatcher
import com.safelense.analysis.match.MatchedCase
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

class AnalysisRunWorkerTests {
    private val runRepository = mock(AnalysisRunRepository::class.java)
    private val propertyRepository = mock(HomePropertyRepository::class.java)
    private val documentRepository = mock(RegistryDocumentRepository::class.java)
    private val evidenceRepository = mock(CollectedEvidenceRepository::class.java)
    private val matchRepository = mock(AnalysisCaseMatchRepository::class.java)
    private val persistedEvidence = mutableListOf<CollectedEvidence>()
    private val persistedMatches = mutableListOf<AnalysisCaseMatch>()
    private val run = AnalysisRun(
        id = 3L,
        propertyId = 2L,
        userId = 1L,
        status = AnalysisRunStatus.QUEUED,
        dataMode = AnalysisDataMode.LIVE,
        idempotencyKey = "run-1",
        forceRefresh = false,
    )

    @Test
    fun `moves through worker states and completes partially when a provider is unavailable`() {
        `when`(runRepository.findByIdForUpdate(3L)).thenReturn(run)
        `when`(propertyRepository.findByIdAndUserId(2L, 1L)).thenReturn(property())
        `when`(documentRepository.findFirstByPropertyIdAndDeletedAtIsNullOrderByIdDesc(2L)).thenReturn(null)
        `when`(evidenceRepository.saveAll(anyList<CollectedEvidence>())).thenAnswer {
            @Suppress("UNCHECKED_CAST")
            persistedEvidence += it.arguments[0] as List<CollectedEvidence>
            persistedEvidence
        }
        `when`(matchRepository.saveAll(anyList<AnalysisCaseMatch>())).thenAnswer {
            @Suppress("UNCHECKED_CAST")
            persistedMatches += it.arguments[0] as List<AnalysisCaseMatch>
            persistedMatches
        }
        val collector = PropertyDataCollector {
            assertThat(run.status).isEqualTo(AnalysisRunStatus.COLLECTING)
            listOf(
                evidence("OFFICIAL_PRICE", """{"amount":50000}""", EvidenceStatus.AVAILABLE),
                evidence("DEPOSIT_INSURANCE_ELIGIBILITY", null, EvidenceStatus.UNAVAILABLE),
            )
        }
        val extractor = RegistryExtractor {
            assertThat(run.status).isEqualTo(AnalysisRunStatus.EXTRACTING_DOCUMENT)
            listOf(evidence("REGISTRY_DOCUMENT", null, EvidenceStatus.NOT_AVAILABLE))
        }
        val matcher = ConsultationCaseMatcher { request ->
            assertThat(run.status).isEqualTo(AnalysisRunStatus.ANALYZING)
            assertThat(request.assessment.confidence).isEqualTo(35)
            ConsultationMatchResult(
                listOf(
                    MatchedCase(
                        databaseId = 101L,
                        caseId = "101",
                        structuredScore = 0.8,
                        semanticScore = 0.9,
                        combinedScore = 0.845,
                        pattern = "보증금반환 · 상담",
                        summary = "비식별 상담 패턴",
                    ),
                ),
                degraded = false,
            )
        }
        val worker = AnalysisRunWorker(
            runRepository,
            propertyRepository,
            documentRepository,
            evidenceRepository,
            matchRepository,
            collector,
            extractor,
            matcher,
            AnalysisRiskRuleEngine(),
            ObjectMapper(),
            ContractDecisionReportGenerator { _, _, _, _ ->
                ContractDecisionReportGeneration(ContractDecisionReportView(), created = true)
            },
            Clock.fixed(NOW, ZoneOffset.UTC),
        )

        val artifacts = worker.execute(3L)

        assertThat(run.startedAt).isEqualTo(NOW)
        assertThat(run.completedAt).isEqualTo(NOW)
        assertThat(run.status).isEqualTo(AnalysisRunStatus.PARTIAL)
        assertThat(persistedEvidence).hasSize(3)
        assertThat(persistedEvidence).allMatch { it.runId == 3L }
        assertThat(persistedMatches).hasSize(1)
        assertThat(persistedMatches.single().consultationCaseId).isEqualTo(101L)
        assertThat(persistedMatches.single().rank).isEqualTo(1)
        assertThat(artifacts?.assessment?.confidence).isEqualTo(35)
        assertThat(artifacts?.matchedCases).hasSize(1)
    }

    private fun evidence(key: String, valueJson: String?, status: EvidenceStatus) =
        CollectedEvidenceCommand(
            evidenceKey = key,
            valueJson = valueJson,
            source = "LIVE_TEST",
            sourceIdentifier = "fixture",
            asOf = Instant.parse("2026-07-01T00:00:00Z"),
            collectedAt = NOW,
            confidence = if (status == EvidenceStatus.AVAILABLE) 90 else 0,
            status = status,
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
