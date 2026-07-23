// 계약 단계별 서류 슬롯과 체크리스트 카탈로그를 검증하는 테스트
package com.safelense.analysis

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class AnalysisTemplateCatalogTests {
    private val catalog = AnalysisTemplateCatalog()

    @Test
    fun `provides six document slots for every stage`() {
        AnalysisStage.entries.forEach { stage ->
            assertThat(catalog.get(stage).documents).hasSize(6)
        }
    }

    @Test
    fun `provides photo checklist counts for every stage`() {
        assertThat(catalog.get(AnalysisStage.BEFORE_CONTRACT).sections.flatMap { it.items }).hasSize(6)
        assertThat(catalog.get(AnalysisStage.DURING_CONTRACT).sections.flatMap { it.items }).hasSize(4)
        assertThat(catalog.get(AnalysisStage.AFTER_CONTRACT).sections.flatMap { it.items }).hasSize(3)
    }

    @Test
    fun `rejects an unknown stage`() {
        assertThatThrownBy { catalog.parse("UNKNOWN") }
            .isInstanceOf(InvalidAnalysisStageException::class.java)
    }

    @Test
    fun `exposes stable document and checklist keys`() {
        val template = catalog.get(AnalysisStage.BEFORE_CONTRACT)

        assertThat(template.documents.map { it.documentType })
            .containsExactly(
                "REGISTRY_CERTIFICATE",
                "BUILDING_LEDGER",
                "LAND_REGISTER",
                "BROKER_LICENSE",
                "LANDLORD_TAX_CERTIFICATE",
                "MANAGEMENT_FEE_STATEMENT",
            )
        assertThat(catalog.itemKeys(AnalysisStage.BEFORE_CONTRACT))
            .contains("VISITED_PROPERTY", "CONFIRMED_LANDLORD_IDENTITY")
    }
}
