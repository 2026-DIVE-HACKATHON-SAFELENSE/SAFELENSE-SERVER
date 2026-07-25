// 비식별 상담 XLSX를 검증하고 임베딩과 함께 멱등 적재하는 서비스
package com.safelense.analysis.match

import java.nio.file.Files
import java.nio.file.Path
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper

const val CONSULTATION_SOURCE = "DIVE_2026_COUNSELING"
const val CONSULTATION_DATASET_VERSION = "2026-v1"

class InvalidConsultationWorkbookException : RuntimeException()

data class ConsultationImportResult(
    val read: Int,
    val upserted: Int,
    val failed: Int,
)

@Service
class ConsultationCaseImportService(
    private val repository: ConsultationCaseRepository,
    private val embeddingClient: EmbeddingClient,
    private val objectMapper: ObjectMapper,
) {
    @Transactional
    fun import(path: Path): ConsultationImportResult {
        if (!Files.isRegularFile(path)) {
            throw InvalidConsultationWorkbookException()
        }
        val rows = readRows(path)
        var upserted = 0
        var failed = rows.failed
        rows.valid.chunked(100).forEach { batch ->
            try {
                val embeddings = embeddingClient.embed(batch.map(ImportRow::embeddingInput))
                if (embeddings.size != batch.size) {
                    throw EmbeddingUnavailableException()
                }
                val cases = batch.zip(embeddings).map { (row, embedding) ->
                    row.toEntity(objectMapper.writeValueAsString(embedding))
                }
                repository.saveAll(cases)
                upserted += cases.size
            } catch (_: Exception) {
                failed += batch.size
            }
        }
        return ConsultationImportResult(
            read = rows.valid.size + rows.failed,
            upserted = upserted,
            failed = failed,
        )
    }

    private fun readRows(path: Path): ImportRows =
        Files.newInputStream(path).use { input ->
            XSSFWorkbook(input).use { workbook ->
                val sheet = workbook.getSheet(SHEET_NAME) ?: throw InvalidConsultationWorkbookException()
                validateHeaders(sheet.getRow(0) ?: throw InvalidConsultationWorkbookException())
                val valid = mutableListOf<ImportRow>()
                var failed = 0
                (1..sheet.lastRowNum).forEach { index ->
                    val values = sheet.getRow(index)?.values().orEmpty()
                    if (values.all(String::isBlank)) {
                        return@forEach
                    }
                    val row = ImportRow.from(values)
                    if (row == null) {
                        failed += 1
                    } else {
                        valid += row
                    }
                }
                ImportRows(valid, failed)
            }
        }

    private fun validateHeaders(row: Row) {
        if (row.values() != EXPECTED_HEADERS) {
            throw InvalidConsultationWorkbookException()
        }
    }

    private fun Row.values(): List<String> =
        (0 until EXPECTED_HEADERS.size).map { index ->
            FORMATTER.formatCellValue(getCell(index)).trim()
        }

    private fun ImportRow.toEntity(embeddingJson: String): ConsultationCase {
        val entity = repository.findBySourceAndExternalCaseId(CONSULTATION_SOURCE, externalCaseId)
            ?: ConsultationCase(
                externalCaseId = externalCaseId,
                source = CONSULTATION_SOURCE,
                datasetVersion = CONSULTATION_DATASET_VERSION,
                sourceGroup = sourceGroup,
                consultationMonth = consultationMonth,
                province = province,
                district = district,
                depositBand = depositBand,
                contractStatus = contractStatus,
                housingType = housingType,
                seniorRights = seniorRights,
                guaranteeStatus = guaranteeStatus,
                disputeType = disputeType,
                progressStage = progressStage,
            )
        entity.datasetVersion = CONSULTATION_DATASET_VERSION
        entity.sourceGroup = sourceGroup
        entity.consultationMonth = consultationMonth
        entity.province = province
        entity.district = district
        entity.depositBand = depositBand
        entity.contractStatus = contractStatus
        entity.housingType = housingType
        entity.seniorRights = seniorRights
        entity.guaranteeStatus = guaranteeStatus
        entity.disputeType = disputeType
        entity.progressStage = progressStage
        entity.situationSummary = situationSummary
        entity.counselorOpinion = counselorOpinion
        entity.specialNotes = specialNotes
        entity.embeddingJson = embeddingJson
        return entity
    }

    private data class ImportRows(
        val valid: List<ImportRow>,
        val failed: Int,
    )

    private data class ImportRow(
        val externalCaseId: String,
        val sourceGroup: String,
        val consultationMonth: String,
        val province: String,
        val district: String,
        val depositBand: String,
        val contractStatus: String,
        val housingType: String,
        val seniorRights: String,
        val guaranteeStatus: String,
        val disputeType: String,
        val progressStage: String,
        val situationSummary: String?,
        val counselorOpinion: String?,
        val specialNotes: String?,
    ) {
        fun embeddingInput(): String =
            listOfNotNull(
                "자료군 $sourceGroup",
                "지역 $province $district",
                "보증금구간 $depositBand",
                "계약상태 $contractStatus",
                "주택유형 $housingType",
                "선순위권리 $seniorRights",
                "보증보험 $guaranteeStatus",
                "분쟁유형 $disputeType",
                "진행단계 $progressStage",
                situationSummary?.let { "상황요약 $it" },
                counselorOpinion?.let { "담당자의견 $it" },
                specialNotes?.let { "특이사항 $it" },
            ).joinToString("\n")

        companion object {
            fun from(values: List<String>): ImportRow? {
                if (values.size != EXPECTED_HEADERS.size || values.take(12).any(String::isBlank)) {
                    return null
                }
                return ImportRow(
                    externalCaseId = values[0],
                    sourceGroup = values[1],
                    consultationMonth = values[2],
                    province = values[3],
                    district = values[4],
                    depositBand = values[5],
                    contractStatus = values[6],
                    housingType = values[7],
                    seniorRights = values[8],
                    guaranteeStatus = values[9],
                    disputeType = values[10],
                    progressStage = values[11],
                    situationSummary = values[12].ifBlank { null },
                    counselorOpinion = values[13].ifBlank { null },
                    specialNotes = values[14].ifBlank { null },
                )
            }
        }
    }

    companion object {
        private const val SHEET_NAME = "비식별_상담데이터"
        private val FORMATTER = DataFormatter()
        private val EXPECTED_HEADERS = listOf(
            "일련번호", "자료군", "상담월", "지역(시도)", "지역(시군구)", "보증금구간",
            "계약상태", "주택유형", "선순위권리", "보증보험", "분쟁유형", "진행단계",
            "상황요약", "담당자의견", "특이사항", "상담변호사",
        )
    }
}
