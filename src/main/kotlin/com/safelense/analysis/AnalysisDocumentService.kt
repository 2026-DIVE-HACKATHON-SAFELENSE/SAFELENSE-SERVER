// 인증 사용자의 분석 서류 슬롯 업로드·교체·삭제를 처리하는 서비스
package com.safelense.analysis

import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile

private const val MAX_DOCUMENT_SIZE = 10L * 1024 * 1024
private val ALLOWED_DOCUMENT_TYPES = setOf("application/pdf", "image/jpeg", "image/png")

@Schema(description = "업로드된 분석 서류 메타데이터")
data class AnalysisDocumentView(
    @field:Schema(description = "서류 ID", example = "17")
    val id: Long,
    @field:Schema(description = "템플릿 서류 종류", example = "REGISTRY_CERTIFICATE")
    val documentType: String,
    @field:Schema(description = "업로드한 원본 파일명", example = "registry.pdf")
    val originalFileName: String,
    @field:Schema(description = "파일 MIME 타입", example = "application/pdf")
    val mimeType: String,
    @field:Schema(description = "파일 크기. 단위는 byte", example = "1048576")
    val fileSize: Long,
)

@Schema(description = "분석 서류 업로드 결과")
data class AnalysisDocumentUploadResult(
    @field:Schema(description = "업로드 또는 교체된 서류")
    val document: AnalysisDocumentView,
    @field:Schema(description = "현재 케이스의 전체 업로드 서류 수", example = "3")
    val uploadedCount: Int,
)

@Service
class AnalysisDocumentService(
    private val caseRepository: AnalysisCaseRepository,
    private val documentRepository: AnalysisDocumentRepository,
    private val resultRepository: AnalysisResultRepository,
    private val catalog: AnalysisTemplateCatalog,
) {
    @Transactional
    fun upload(
        userId: Long,
        caseId: Long,
        documentType: String,
        file: MultipartFile,
    ): AnalysisDocumentUploadResult {
        val analysisCase = caseRepository.findByIdAndUserIdForUpdate(caseId, userId)
            ?: throw AnalysisCaseNotFoundException()
        if (resultRepository.existsByCaseId(caseId)) throw AnalysisCaseLockedException()
        val fileName = file.originalFilename?.trim().orEmpty()
        val mimeType = file.contentType.orEmpty()
        if (!catalog.supportsDocument(analysisCase.stage, documentType) ||
            file.isEmpty ||
            fileName.isEmpty() ||
            fileName.length > 255 ||
            mimeType !in ALLOWED_DOCUMENT_TYPES
        ) {
            throw InvalidAnalysisDocumentException()
        }
        if (file.size > MAX_DOCUMENT_SIZE) throw AnalysisDocumentTooLargeException()

        val document = documentRepository.findByCaseIdAndDocumentType(caseId, documentType)
            ?.apply {
                originalFileName = fileName
                this.mimeType = mimeType
                fileSize = file.size
                content = file.bytes
            }
            ?: AnalysisDocument(
                caseId = caseId,
                documentType = documentType,
                originalFileName = fileName,
                mimeType = mimeType,
                fileSize = file.size,
                content = file.bytes,
            )
        val saved = documentRepository.save(document)
        return AnalysisDocumentUploadResult(
            document = saved.toView(),
            uploadedCount = documentRepository.countByCaseId(caseId).toInt(),
        )
    }

    @Transactional
    fun delete(userId: Long, caseId: Long, documentId: Long) {
        caseRepository.findByIdAndUserIdForUpdate(caseId, userId)
            ?: throw AnalysisCaseNotFoundException()
        if (resultRepository.existsByCaseId(caseId)) throw AnalysisCaseLockedException()
        if (documentRepository.deleteByIdAndCaseId(documentId, caseId) == 0) {
            throw AnalysisDocumentNotFoundException()
        }
    }

    private fun AnalysisDocument.toView(): AnalysisDocumentView =
        AnalysisDocumentView(
            id = requireNotNull(id),
            documentType = documentType,
            originalFileName = originalFileName,
            mimeType = mimeType,
            fileSize = fileSize,
        )
}
