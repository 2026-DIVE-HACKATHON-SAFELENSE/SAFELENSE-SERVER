// 분석 케이스의 슬롯별 업로드 문서를 조회하는 저장소
package com.safelense.analysis

import org.springframework.data.jpa.repository.JpaRepository

interface AnalysisDocumentRepository : JpaRepository<AnalysisDocument, Long> {
    fun findAllByCaseId(caseId: Long): List<AnalysisDocument>
    fun findByCaseIdAndDocumentType(caseId: Long, documentType: String): AnalysisDocument?
    fun findByIdAndCaseId(id: Long, caseId: Long): AnalysisDocument?
    fun countByCaseId(caseId: Long): Long
}
