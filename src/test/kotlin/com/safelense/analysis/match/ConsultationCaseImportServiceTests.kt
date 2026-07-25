// 비식별 상담 XLSX의 헤더 검증과 임베딩 멱등 적재를 검증하는 테스트
package com.safelense.analysis.match

import com.safelense.analysis.interpretation.OpenAiProperties
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.ArgumentMatchers.anyList
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import tools.jackson.databind.ObjectMapper

class ConsultationCaseImportServiceTests {
    @TempDir
    lateinit var tempDir: Path

    private val repository = mock(ConsultationCaseRepository::class.java)
    private val embeddingClient = RecordingEmbeddingClient()
    private val saved = mutableListOf<ConsultationCase>()
    private val service = ConsultationCaseImportService(
        repository,
        embeddingClient,
        ObjectMapper(),
        OpenAiProperties(apiKey = "test-key"),
        Clock.fixed(Instant.parse("2026-07-26T00:00:00Z"), ZoneOffset.UTC),
    )

    @Test
    fun `imports the named sheet and preserves nullable text`() {
        val path = workbook(
            EXPECTED_HEADERS,
            listOf(
                "1", "임차in", "2026-01", "서울특별시", "중구", "1억~2억",
                "계약전", "아파트", "근저당", "미가입", "보증금", "상담",
                "상황 요약", "", "", "비식별 변호사",
            ),
        )
        prepareRepository()

        val result = service.import(path)

        assertThat(result).isEqualTo(ConsultationImportResult(read = 1, upserted = 1, failed = 0))
        assertThat(saved.single().externalCaseId).isEqualTo("DIVE-2026-1")
        assertThat(saved.single().source).isEqualTo("DIVE_2026_COUNSELING")
        assertThat(saved.single().datasetVersion).isEqualTo("2026-v1")
        assertThat(saved.single().attorneyCode).isEqualTo("비식별 변호사")
        assertThat(saved.single().counselorOpinion).isNull()
        assertThat(saved.single().specialNotes).isNull()
        assertThat(saved.single().embeddingJson).isEqualTo("[0.1,0.2]")
        assertThat(saved.single().embeddingModel).isEqualTo("text-embedding-3-small")
        assertThat(saved.single().embeddingCreatedAt).isEqualTo(Instant.parse("2026-07-26T00:00:00Z"))
        assertThat(embeddingClient.inputs.single()).doesNotContain("비식별 변호사")
    }

    @Test
    fun `stores an empty summary case without requesting an embedding`() {
        val path = workbook(
            EXPECTED_HEADERS,
            listOf(
                "2", "임차in", "2026-01", "서울특별시", "중구", "1억~2억",
                "계약전", "아파트", "근저당", "미가입", "보증금", "상담",
                "", "의견", "특이사항", "비식별 변호사",
            ),
        )
        `when`(repository.findBySourceAndExternalCaseId("DIVE_2026_COUNSELING", "DIVE-2026-2"))
            .thenReturn(null)
        captureSaves()

        val result = service.import(path)

        assertThat(result).isEqualTo(ConsultationImportResult(read = 1, upserted = 1, failed = 0))
        assertThat(embeddingClient.inputs).isEmpty()
        assertThat(saved.single().embeddingJson).isNull()
        assertThat(saved.single().embeddingModel).isNull()
        assertThat(saved.single().embeddingCreatedAt).isNull()
    }

    @Test
    fun `updates an existing source case instead of inserting a duplicate`() {
        val path = workbook(
            EXPECTED_HEADERS,
            listOf(
                "1", "임차in", "2026-02", "서울특별시", "중구", "2억~3억",
                "계약후", "아파트", "없음", "가입", "갱신", "종결",
                "", "새 의견", "", "비식별 변호사",
            ),
        )
        val existing = case(externalCaseId = "1", depositBand = "기존")
        `when`(repository.findBySourceAndExternalCaseId("DIVE_2026_COUNSELING", "DIVE-2026-1"))
            .thenReturn(existing)
        captureSaves()

        val result = service.import(path)

        assertThat(result.upserted).isEqualTo(1)
        assertThat(saved.single()).isSameAs(existing)
        assertThat(existing.depositBand).isEqualTo("2억~3억")
        assertThat(existing.situationSummary).isNull()
        assertThat(existing.counselorOpinion).isEqualTo("새 의견")
    }

    @Test
    fun `rejects a workbook with a changed header before embedding`() {
        val wrongHeaders = EXPECTED_HEADERS.toMutableList().apply { set(12, "다른헤더") }
        val path = workbook(wrongHeaders, List(16) { "값" })

        assertThatThrownBy { service.import(path) }
            .isInstanceOf(InvalidConsultationWorkbookException::class.java)
        assertThat(embeddingClient.inputs).isEmpty()
    }

    private fun prepareRepository() {
        `when`(repository.findBySourceAndExternalCaseId("DIVE_2026_COUNSELING", "DIVE-2026-1"))
            .thenReturn(null)
        captureSaves()
    }

    private fun captureSaves() {
        `when`(repository.saveAllAndFlush(anyList<ConsultationCase>())).thenAnswer {
            @Suppress("UNCHECKED_CAST")
            val cases = it.arguments[0] as List<ConsultationCase>
            saved += cases
            cases
        }
    }

    private fun workbook(headers: List<String>, rowValues: List<String>): Path {
        val path = tempDir.resolve("consultations.xlsx")
        XSSFWorkbook().use { workbook ->
            val sheet = workbook.createSheet("비식별_상담데이터")
            val header = sheet.createRow(0)
            headers.forEachIndexed { index, value -> header.createCell(index).setCellValue(value) }
            val row = sheet.createRow(1)
            rowValues.forEachIndexed { index, value -> row.createCell(index).setCellValue(value) }
            path.toFile().outputStream().use(workbook::write)
        }
        return path
    }

    private fun case(externalCaseId: String, depositBand: String) =
        ConsultationCase(
            externalCaseId = "DIVE-2026-$externalCaseId",
            source = "DIVE_2026_COUNSELING",
            datasetVersion = "2026-v1",
            sourceGroup = "임차in",
            consultationMonth = "2026-01",
            province = "서울특별시",
            district = "중구",
            depositBand = depositBand,
            contractStatus = "계약전",
            housingType = "아파트",
            seniorRights = "근저당",
            guaranteeStatus = "미가입",
            disputeType = "보증금",
            progressStage = "상담",
            attorneyCode = "비식별 변호사",
        )

    companion object {
        private val EXPECTED_HEADERS = listOf(
            "일련번호", "자료군", "상담월", "지역(시도)", "지역(시군구)", "보증금구간",
            "계약상태", "주택유형", "선순위권리", "보증보험", "분쟁유형", "진행단계",
            "상황요약", "담당자의견", "특이사항", "상담변호사",
        )
    }
}

private class RecordingEmbeddingClient : EmbeddingClient {
    val inputs = mutableListOf<String>()

    override fun embed(inputs: List<String>): List<List<Double>> {
        this.inputs += inputs
        return inputs.map { listOf(0.1, 0.2) }
    }
}
