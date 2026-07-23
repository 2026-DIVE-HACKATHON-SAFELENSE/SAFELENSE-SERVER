// 분석 케이스의 슬롯별 업로드 문서를 조회하는 저장소
package com.safelense.analysis

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

data class AnalysisDocumentMetadata(
    val id: Long,
    val documentType: String,
    val originalFileName: String,
    val mimeType: String,
    val fileSize: Long,
)

interface AnalysisDocumentRepository : JpaRepository<AnalysisDocument, Long> {
    @Query(
        """
        select new com.safelense.analysis.AnalysisDocumentMetadata(
            document.id,
            document.documentType,
            document.originalFileName,
            document.mimeType,
            document.fileSize
        )
        from AnalysisDocument document
        where document.caseId = :caseId
        """,
    )
    fun findAllMetadataByCaseId(@Param("caseId") caseId: Long): List<AnalysisDocumentMetadata>

    fun findByCaseIdAndDocumentType(caseId: Long, documentType: String): AnalysisDocument?
    fun findByIdAndCaseId(id: Long, caseId: Long): AnalysisDocument?
    fun countByCaseId(caseId: Long): Long
}
