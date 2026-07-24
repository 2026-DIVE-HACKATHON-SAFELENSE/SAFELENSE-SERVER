// 계약 단계별 서류 슬롯과 체크리스트 정의를 제공하는 불변 카탈로그
package com.safelense.analysis

import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.stereotype.Component

const val ANALYSIS_TEMPLATE_VERSION = "2026-07-24-v1"

enum class AnalysisStage {
    BEFORE_CONTRACT,
    DURING_CONTRACT,
    AFTER_CONTRACT,
}

@Schema(description = "분석 단계에서 요구하는 서류 템플릿")
data class AnalysisDocumentTemplate(
    val documentType: String,
    val label: String,
    val required: Boolean,
)

@Schema(description = "체크리스트 항목 템플릿")
data class AnalysisChecklistItemTemplate(
    val itemKey: String,
    val label: String,
)

@Schema(description = "체크리스트 섹션 템플릿")
data class AnalysisChecklistSectionTemplate(
    val sectionKey: String,
    val label: String,
    val items: List<AnalysisChecklistItemTemplate>,
)

@Schema(description = "계약 단계별 분석 서류와 체크리스트 템플릿")
data class AnalysisTemplate(
    val stage: AnalysisStage,
    val version: String,
    val documents: List<AnalysisDocumentTemplate>,
    val sections: List<AnalysisChecklistSectionTemplate>,
)

class InvalidAnalysisStageException : RuntimeException()

@Component
class AnalysisTemplateCatalog {
    private val commonDocuments = listOf(
        AnalysisDocumentTemplate("REGISTRY_CERTIFICATE", "등기부등본 확인", true),
        AnalysisDocumentTemplate("BUILDING_LEDGER", "건축물대장 확인", true),
        AnalysisDocumentTemplate("LAND_REGISTER", "토지대장 확인", false),
        AnalysisDocumentTemplate("BROKER_LICENSE", "공인중개사 자격증 확인", true),
        AnalysisDocumentTemplate("LANDLORD_TAX_CERTIFICATE", "임대인 납세 확인", true),
        AnalysisDocumentTemplate("MANAGEMENT_FEE_STATEMENT", "관리비 내역 확인", false),
    )

    private val templates = mapOf(
        AnalysisStage.BEFORE_CONTRACT to AnalysisTemplate(
            stage = AnalysisStage.BEFORE_CONTRACT,
            version = ANALYSIS_TEMPLATE_VERSION,
            documents = commonDocuments,
            sections = listOf(
                AnalysisChecklistSectionTemplate(
                    sectionKey = "FIELD_CHECK",
                    label = "현장 확인",
                    items = listOf(
                        AnalysisChecklistItemTemplate("VISITED_PROPERTY", "집을 직접 방문했어요."),
                        AnalysisChecklistItemTemplate("CHECKED_INTERIOR", "집 내부 상태를 확인했어요."),
                        AnalysisChecklistItemTemplate("CHECKED_SURROUNDINGS", "주변 환경을 확인했어요."),
                    ),
                ),
                AnalysisChecklistSectionTemplate(
                    sectionKey = "DOCUMENT_CHECK",
                    label = "서류 확인",
                    items = listOf(
                        AnalysisChecklistItemTemplate("CONFIRMED_OWNER", "등기부등본의 소유자를 확인했어요."),
                        AnalysisChecklistItemTemplate("CONFIRMED_LANDLORD_IDENTITY", "임대인 신분을 확인했어요."),
                        AnalysisChecklistItemTemplate("CONFIRMED_CONTRACT_TERMS", "계약 조건을 확인했어요."),
                    ),
                ),
            ),
        ),
        AnalysisStage.DURING_CONTRACT to AnalysisTemplate(
            stage = AnalysisStage.DURING_CONTRACT,
            version = ANALYSIS_TEMPLATE_VERSION,
            documents = commonDocuments,
            sections = listOf(
                AnalysisChecklistSectionTemplate(
                    sectionKey = "PARTY_CHECK",
                    label = "계약 당사자",
                    items = listOf(
                        AnalysisChecklistItemTemplate("MATCHED_CONTRACT_PARTIES", "계약 당사자 정보를 확인했어요."),
                        AnalysisChecklistItemTemplate("CONFIRMED_AGENT_AUTHORITY", "대리 계약 권한을 확인했어요."),
                    ),
                ),
                AnalysisChecklistSectionTemplate(
                    sectionKey = "CONTRACT_CHECK",
                    label = "계약서 확인",
                    items = listOf(
                        AnalysisChecklistItemTemplate("REVIEWED_SPECIAL_TERMS", "계약서의 특약 사항을 확인했어요."),
                        AnalysisChecklistItemTemplate("SIGNED_CONTRACT", "계약서에 서명·날인했어요."),
                    ),
                ),
            ),
        ),
        AnalysisStage.AFTER_CONTRACT to AnalysisTemplate(
            stage = AnalysisStage.AFTER_CONTRACT,
            version = ANALYSIS_TEMPLATE_VERSION,
            documents = commonDocuments,
            sections = listOf(
                AnalysisChecklistSectionTemplate(
                    sectionKey = "MOVE_IN",
                    label = "입주 절차",
                    items = listOf(
                        AnalysisChecklistItemTemplate("RECEIVED_FIXED_DATE", "확정일자를 받았어요."),
                        AnalysisChecklistItemTemplate("COMPLETED_MOVE_IN_REPORT", "전입신고를 완료했어요."),
                    ),
                ),
                AnalysisChecklistSectionTemplate(
                    sectionKey = "GUARANTEE",
                    label = "보증 확인",
                    items = listOf(
                        AnalysisChecklistItemTemplate("CHECKED_DEPOSIT_GUARANTEE", "보증금 반환보증 가입 여부를 확인했어요."),
                    ),
                ),
            ),
        ),
    )

    fun parse(rawStage: String): AnalysisStage =
        runCatching { AnalysisStage.valueOf(rawStage) }
            .getOrElse { throw InvalidAnalysisStageException() }

    fun get(stage: AnalysisStage): AnalysisTemplate = templates.getValue(stage)

    fun supportsDocument(stage: AnalysisStage, documentType: String): Boolean =
        get(stage).documents.any { it.documentType == documentType }

    fun itemKeys(stage: AnalysisStage): List<String> =
        get(stage).sections.flatMap { section -> section.items.map { it.itemKey } }
}
