// 분석 케이스의 한 서류 슬롯에 업로드된 파일을 저장하는 엔티티
package com.safelense.analysis

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "analysis_documents")
class AnalysisDocument(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @Column(name = "case_id", nullable = false)
    val caseId: Long,
    @Column(name = "document_type", nullable = false, length = 64)
    val documentType: String,
    @Column(name = "original_file_name", nullable = false, length = 255)
    var originalFileName: String,
    @Column(name = "mime_type", nullable = false, length = 100)
    var mimeType: String,
    @Column(name = "file_size", nullable = false)
    var fileSize: Long,
    @Column(nullable = false, columnDefinition = "bytea")
    var content: ByteArray,
)
