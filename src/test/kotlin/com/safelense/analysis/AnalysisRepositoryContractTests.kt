// 분석 입력 저장소가 BLOB 비로딩과 즉시 bulk 변경 계약을 지키는지 검증하는 테스트
package com.safelense.analysis

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

class AnalysisRepositoryContractTests {
    @Test
    fun `checklist replacement uses an explicit bulk delete`() {
        val method = AnalysisChecklistAnswerRepository::class.java.getMethod(
            "deleteAllByCaseId",
            Long::class.javaPrimitiveType,
        )

        assertThat(method.getAnnotation(Modifying::class.java)).isNotNull()
        assertThat(method.getAnnotation(Query::class.java)?.value)
            .contains("delete from AnalysisChecklistAnswer")
        assertThat(method.returnType).isEqualTo(Int::class.javaPrimitiveType)
    }

    @Test
    fun `case detail document query selects metadata without content`() {
        val method = AnalysisDocumentRepository::class.java.methods
            .find { it.name == "findAllMetadataByCaseId" }

        assertThat(method).isNotNull()
        assertThat(method?.genericReturnType?.typeName).contains("AnalysisDocumentMetadata")
        assertThat(method?.getAnnotation(Query::class.java)?.value)
            .contains(
                "document.id",
                "document.documentType",
                "document.originalFileName",
                "document.mimeType",
                "document.fileSize",
            )
            .doesNotContain("document.content")
    }

    @Test
    fun `document deletion uses a scoped explicit bulk delete`() {
        val method = AnalysisDocumentRepository::class.java.methods
            .find { it.name == "deleteByIdAndCaseId" }

        assertThat(method).isNotNull()
        assertThat(method?.getAnnotation(Modifying::class.java)).isNotNull()
        assertThat(method?.getAnnotation(Query::class.java)?.value)
            .contains(
                "delete from AnalysisDocument",
                "document.id = :documentId",
                "document.caseId = :caseId",
            )
        assertThat(method?.returnType).isEqualTo(Int::class.javaPrimitiveType)
    }
}
