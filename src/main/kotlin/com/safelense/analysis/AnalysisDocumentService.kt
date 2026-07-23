// 인증 사용자의 분석 서류 슬롯 업로드·교체·삭제를 처리하는 서비스
package com.safelense.analysis

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile

private const val MAX_DOCUMENT_SIZE = 10L * 1024 * 1024
private val ALLOWED_DOCUMENT_TYPES = setOf("application/pdf", "image/jpeg", "image/png")

data class AnalysisDocumentView(
    val id: Long,
    val documentType: String,
    val originalFileName: String,
    val mimeType: String,
    val fileSize: Long,
)

data class AnalysisDocumentUploadResult(
    val document: AnalysisDocumentView,
    val uploadedCount: Int,
)

@Service
class AnalysisDocumentService(
    private val caseRepository: AnalysisCaseRepository,
    private val documentRepository: AnalysisDocumentRepository,
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
        val document = documentRepository.findByIdAndCaseId(documentId, caseId)
            ?: throw AnalysisDocumentNotFoundException()
        documentRepository.delete(document)
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
