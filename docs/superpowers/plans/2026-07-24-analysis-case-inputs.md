# Analysis Case Input APIs Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 사진의 계약 전·중·후 흐름에 필요한 단계별 템플릿, 분석 케이스, 서류 업로드·삭제, 체크리스트 저장 API 6개를 구현한다.

**Architecture:** `com.safelense.analysis` 패키지 안에 불변 템플릿 카탈로그, 입력 영속 모델, 케이스·문서·체크리스트 서비스를 각각 둔다. MySQL에는 케이스, 문서 바이트, 불리언 체크리스트 답변만 저장하고 위험 분석·결과 생성은 포함하지 않는다.

**Tech Stack:** Kotlin 2.3.10, JVM 24, Spring Boot 4.1.0, Spring MVC, Spring Security, Spring Data JPA, Flyway, MySQL, Jackson 3, JUnit 5, Mockito.

## Global Constraints

- 사진과 노션 명세가 다르면 사진을 우선한다.
- 계약 단계는 `BEFORE_CONTRACT`, `DURING_CONTRACT`, `AFTER_CONTRACT`만 허용한다.
- 각 단계는 정확히 6개의 서류 슬롯을 제공한다.
- 서류와 체크리스트는 비어 있거나 일부만 입력돼도 정상 상태다.
- 체크리스트 답변은 `checked: Boolean`으로 저장한다.
- 허용 파일은 PDF, JPEG, PNG이며 파일당 최대 크기는 10MiB다.
- 파일은 MySQL `MEDIUMBLOB`에 저장하고 내용은 해석하지 않는다.
- 모든 케이스·문서 접근은 인증 사용자 ID로 격리한다.
- `POST /api/v1/analysis-cases/{caseId}/analyze`와 분석 결과 테이블은 만들지 않는다.
- 새 의존성을 추가하지 않는다.
- 모든 새 Kotlin·SQL 소스 파일은 첫 줄에 역할을 설명하는 한국어 주석을 둔다.
- 기존 인증·주택 코드와 무관한 리팩터링은 하지 않는다.

---

### Task 1: 단계별 분석 템플릿 API

**Files:**
- Create: `src/main/kotlin/com/safelense/analysis/AnalysisTemplate.kt`
- Create: `src/main/kotlin/com/safelense/analysis/AnalysisTemplateController.kt`
- Modify: `src/main/kotlin/com/safelense/auth/presentation/ApiExceptionHandler.kt`
- Test: `src/test/kotlin/com/safelense/analysis/AnalysisTemplateCatalogTests.kt`
- Test: `src/test/kotlin/com/safelense/analysis/AnalysisTemplateControllerTests.kt`

**Interfaces:**
- Produces: `AnalysisStage`, `AnalysisTemplateCatalog.parse(rawStage)`, `AnalysisTemplateCatalog.get(stage)`, `supportsDocument(stage, documentType)`, `itemKeys(stage)`.
- Produces: `GET /api/v1/analysis-templates/{stage}`.

- [ ] **Step 1: 템플릿 카탈로그 실패 테스트 작성**

`AnalysisTemplateCatalogTests.kt`에 다음 핵심 계약을 작성한다.

```kotlin
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
```

- [ ] **Step 2: 카탈로그 테스트 실패 확인**

Run:

```bash
./gradlew test --tests 'com.safelense.analysis.AnalysisTemplateCatalogTests'
```

Expected: `AnalysisTemplateCatalog`과 관련 타입을 찾을 수 없어 컴파일 실패한다.

- [ ] **Step 3: 불변 템플릿 카탈로그 구현**

`AnalysisTemplate.kt`에 다음 타입과 데이터를 구현한다.

```kotlin
// 계약 단계별 서류 슬롯과 체크리스트 정의를 제공하는 불변 카탈로그
package com.safelense.analysis

import org.springframework.stereotype.Component

const val ANALYSIS_TEMPLATE_VERSION = "2026-07-24-v1"

enum class AnalysisStage {
    BEFORE_CONTRACT,
    DURING_CONTRACT,
    AFTER_CONTRACT,
}

data class AnalysisDocumentTemplate(
    val documentType: String,
    val label: String,
    val required: Boolean,
)

data class AnalysisChecklistItemTemplate(
    val itemKey: String,
    val label: String,
)

data class AnalysisChecklistSectionTemplate(
    val sectionKey: String,
    val label: String,
    val items: List<AnalysisChecklistItemTemplate>,
)

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
```

- [ ] **Step 4: 카탈로그 테스트 통과 확인**

Run:

```bash
./gradlew test --tests 'com.safelense.analysis.AnalysisTemplateCatalogTests'
```

Expected: PASS.

- [ ] **Step 5: 템플릿 HTTP 계약 실패 테스트 작성**

`AnalysisTemplateControllerTests.kt`에서 정상 단계와 잘못된 단계를 검증한다.

```kotlin
// 단계별 분석 템플릿 HTTP 응답을 검증하는 MVC 테스트
package com.safelense.analysis

import com.safelense.auth.presentation.ApiExceptionHandler
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class AnalysisTemplateControllerTests {
    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders
            .standaloneSetup(AnalysisTemplateController(AnalysisTemplateCatalog()))
            .setControllerAdvice(ApiExceptionHandler())
            .setMessageConverters(JacksonJsonHttpMessageConverter())
            .build()
    }

    @Test
    fun `returns the stage template`() {
        mockMvc.perform(get("/api/v1/analysis-templates/BEFORE_CONTRACT"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.stage").value("BEFORE_CONTRACT"))
            .andExpect(jsonPath("$.version").value(ANALYSIS_TEMPLATE_VERSION))
            .andExpect(jsonPath("$.documents.length()").value(6))
            .andExpect(jsonPath("$.sections[0].sectionKey").value("FIELD_CHECK"))
    }

    @Test
    fun `rejects an unknown stage`() {
        mockMvc.perform(get("/api/v1/analysis-templates/UNKNOWN"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_STAGE"))
    }
}
```

- [ ] **Step 6: 템플릿 컨트롤러와 오류 매핑 구현**

`AnalysisTemplateController.kt`를 구현한다.

```kotlin
// 계약 단계별 서류와 체크리스트 템플릿 조회 API를 제공하는 컨트롤러
package com.safelense.analysis

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/analysis-templates")
class AnalysisTemplateController(
    private val catalog: AnalysisTemplateCatalog,
) {
    @GetMapping("/{stage}")
    fun get(@PathVariable stage: String): AnalysisTemplate =
        catalog.get(catalog.parse(stage))
}
```

`ApiExceptionHandler.kt`에 다음 매핑을 추가한다.

```kotlin
@ExceptionHandler(InvalidAnalysisStageException::class)
fun handleInvalidAnalysisStage(): ResponseEntity<ApiError> =
    error(HttpStatus.BAD_REQUEST, "INVALID_STAGE", "Analysis stage is invalid.")
```

- [ ] **Step 7: 템플릿 API 테스트 통과 확인**

Run:

```bash
./gradlew test --tests 'com.safelense.analysis.AnalysisTemplate*'
```

Expected: PASS.

- [ ] **Step 8: 템플릿 API 커밋**

```bash
git add src/main/kotlin/com/safelense/analysis/AnalysisTemplate.kt src/main/kotlin/com/safelense/analysis/AnalysisTemplateController.kt src/main/kotlin/com/safelense/auth/presentation/ApiExceptionHandler.kt src/test/kotlin/com/safelense/analysis/AnalysisTemplateCatalogTests.kt src/test/kotlin/com/safelense/analysis/AnalysisTemplateControllerTests.kt
git commit -m "feat: 분석 단계 템플릿 API 추가"
```

### Task 2: 분석 입력 영속 모델과 마이그레이션

**Files:**
- Create: `src/main/resources/db/migration/V4__create_analysis_case_inputs.sql`
- Create: `src/main/kotlin/com/safelense/analysis/AnalysisCase.kt`
- Create: `src/main/kotlin/com/safelense/analysis/AnalysisDocument.kt`
- Create: `src/main/kotlin/com/safelense/analysis/AnalysisChecklistAnswer.kt`
- Create: `src/main/kotlin/com/safelense/analysis/AnalysisCaseRepository.kt`
- Create: `src/main/kotlin/com/safelense/analysis/AnalysisDocumentRepository.kt`
- Create: `src/main/kotlin/com/safelense/analysis/AnalysisChecklistAnswerRepository.kt`
- Modify: `src/main/kotlin/com/safelense/property/HomePropertyRepository.kt`
- Test: `src/test/kotlin/com/safelense/analysis/AnalysisInputMigrationTests.kt`

**Interfaces:**
- Consumes: `AnalysisStage`.
- Produces: `analysis_cases`, `analysis_documents`, `analysis_checklist_answers` 테이블.
- Produces: 사용자 소유 케이스 조회와 케이스 행 잠금 저장소 메서드.

- [ ] **Step 1: V4 마이그레이션 실패 테스트 작성**

```kotlin
// 분석 케이스 입력 테이블의 핵심 제약을 검증하는 테스트
package com.safelense.analysis

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource

class AnalysisInputMigrationTests {
    @Test
    fun `analysis input migration defines ownership and slot constraints`() {
        val migration = ClassPathResource("db/migration/V4__create_analysis_case_inputs.sql")

        assertThat(migration.exists()).isTrue()
        val sql = migration.inputStream.bufferedReader().use { it.readText() }
        assertThat(sql).contains("CREATE TABLE analysis_cases")
        assertThat(sql).contains("CREATE TABLE analysis_documents")
        assertThat(sql).contains("CREATE TABLE analysis_checklist_answers")
        assertThat(sql).contains("MEDIUMBLOB NOT NULL")
        assertThat(sql).contains("UNIQUE (case_id, document_type)")
        assertThat(sql).contains("UNIQUE (case_id, item_key)")
        assertThat(sql).contains("FOREIGN KEY (property_id) REFERENCES home_properties(id)")
    }
}
```

- [ ] **Step 2: 마이그레이션 테스트 실패 확인**

Run:

```bash
./gradlew test --tests 'com.safelense.analysis.AnalysisInputMigrationTests'
```

Expected: V4 파일 부재로 FAIL.

- [ ] **Step 3: V4 마이그레이션 구현**

```sql
-- 분석 케이스와 선택 입력 서류·체크리스트를 저장하는 테이블
CREATE TABLE analysis_cases (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    property_id BIGINT NOT NULL,
    stage VARCHAR(32) NOT NULL,
    template_version VARCHAR(32) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    INDEX idx_analysis_cases_user_id (user_id),
    CONSTRAINT fk_analysis_cases_user_id FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_analysis_cases_property_id FOREIGN KEY (property_id) REFERENCES home_properties(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE analysis_documents (
    id BIGINT NOT NULL AUTO_INCREMENT,
    case_id BIGINT NOT NULL,
    document_type VARCHAR(64) NOT NULL,
    original_file_name VARCHAR(255) NOT NULL,
    mime_type VARCHAR(100) NOT NULL,
    file_size BIGINT NOT NULL,
    content MEDIUMBLOB NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_analysis_documents_case_type UNIQUE (case_id, document_type),
    CONSTRAINT fk_analysis_documents_case_id FOREIGN KEY (case_id) REFERENCES analysis_cases(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE analysis_checklist_answers (
    id BIGINT NOT NULL AUTO_INCREMENT,
    case_id BIGINT NOT NULL,
    item_key VARCHAR(100) NOT NULL,
    checked BOOLEAN NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_analysis_answers_case_item UNIQUE (case_id, item_key),
    CONSTRAINT fk_analysis_answers_case_id FOREIGN KEY (case_id) REFERENCES analysis_cases(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

- [ ] **Step 4: 마이그레이션 테스트 통과 확인**

Run:

```bash
./gradlew test --tests 'com.safelense.analysis.AnalysisInputMigrationTests'
```

Expected: PASS.

- [ ] **Step 5: JPA 엔티티와 저장소 구현**

`AnalysisCase.kt`는 다음 필드를 매핑한다.

```kotlin
// 사용자와 주택에 귀속된 계약 단계별 분석 입력 케이스 엔티티
package com.safelense.analysis

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "analysis_cases")
class AnalysisCase(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @Column(name = "user_id", nullable = false)
    val userId: Long,
    @Column(name = "property_id", nullable = false)
    val propertyId: Long,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    val stage: AnalysisStage,
    @Column(name = "template_version", nullable = false, length = 32)
    val templateVersion: String,
)
```

`AnalysisDocument.kt`는 슬롯 교체를 위해 메타데이터와 바이트 필드를 가변으로 둔다.

```kotlin
// 분석 케이스의 한 서류 슬롯에 업로드된 파일을 저장하는 엔티티
package com.safelense.analysis

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Lob
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
    @Lob
    @Column(nullable = false, columnDefinition = "MEDIUMBLOB")
    var content: ByteArray,
)
```

`AnalysisChecklistAnswer.kt`는 불리언 답변을 저장한다.

```kotlin
// 분석 케이스의 체크리스트 문항별 불리언 답변을 저장하는 엔티티
package com.safelense.analysis

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "analysis_checklist_answers")
class AnalysisChecklistAnswer(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @Column(name = "case_id", nullable = false)
    val caseId: Long,
    @Column(name = "item_key", nullable = false, length = 100)
    val itemKey: String,
    @Column(nullable = false)
    val checked: Boolean,
)
```

저장소 인터페이스는 다음 시그니처를 정확히 제공한다.

```kotlin
// 사용자 소유 분석 케이스 조회와 입력 변경 잠금을 제공하는 저장소
package com.safelense.analysis

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface AnalysisCaseRepository : JpaRepository<AnalysisCase, Long> {
    fun findByIdAndUserId(id: Long, userId: Long): AnalysisCase?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select analysisCase from AnalysisCase analysisCase where analysisCase.id = :id and analysisCase.userId = :userId")
    fun findByIdAndUserIdForUpdate(
        @Param("id") id: Long,
        @Param("userId") userId: Long,
    ): AnalysisCase?
}
```

```kotlin
// 분석 케이스의 슬롯별 업로드 문서를 조회하는 저장소
package com.safelense.analysis

import org.springframework.data.jpa.repository.JpaRepository

interface AnalysisDocumentRepository : JpaRepository<AnalysisDocument, Long> {
    fun findAllByCaseId(caseId: Long): List<AnalysisDocument>
    fun findByCaseIdAndDocumentType(caseId: Long, documentType: String): AnalysisDocument?
    fun findByIdAndCaseId(id: Long, caseId: Long): AnalysisDocument?
    fun countByCaseId(caseId: Long): Long
}
```

```kotlin
// 분석 케이스의 체크리스트 답변 전체 교체를 지원하는 저장소
package com.safelense.analysis

import org.springframework.data.jpa.repository.JpaRepository

interface AnalysisChecklistAnswerRepository : JpaRepository<AnalysisChecklistAnswer, Long> {
    fun findAllByCaseId(caseId: Long): List<AnalysisChecklistAnswer>
    fun deleteAllByCaseId(caseId: Long)
}
```

`HomePropertyRepository.kt`에 소유권 검증 메서드를 추가한다.

```kotlin
fun findByIdAndUserId(id: Long, userId: Long): HomeProperty?
```

- [ ] **Step 6: 분석 패키지 컴파일 확인**

Run:

```bash
./gradlew compileKotlin
```

Expected: PASS.

- [ ] **Step 7: 영속 모델 커밋**

```bash
git add src/main/resources/db/migration/V4__create_analysis_case_inputs.sql src/main/kotlin/com/safelense/analysis/AnalysisCase.kt src/main/kotlin/com/safelense/analysis/AnalysisDocument.kt src/main/kotlin/com/safelense/analysis/AnalysisChecklistAnswer.kt src/main/kotlin/com/safelense/analysis/AnalysisCaseRepository.kt src/main/kotlin/com/safelense/analysis/AnalysisDocumentRepository.kt src/main/kotlin/com/safelense/analysis/AnalysisChecklistAnswerRepository.kt src/main/kotlin/com/safelense/property/HomePropertyRepository.kt src/test/kotlin/com/safelense/analysis/AnalysisInputMigrationTests.kt
git commit -m "feat: 분석 케이스 입력 영속 모델 추가"
```

### Task 3: 분석 케이스 생성·조회 API

**Files:**
- Create: `src/main/kotlin/com/safelense/analysis/AnalysisExceptions.kt`
- Create: `src/main/kotlin/com/safelense/analysis/AnalysisCaseService.kt`
- Create: `src/main/kotlin/com/safelense/analysis/AnalysisCaseController.kt`
- Modify: `src/main/kotlin/com/safelense/auth/presentation/ApiExceptionHandler.kt`
- Test: `src/test/kotlin/com/safelense/analysis/AnalysisCaseServiceTests.kt`
- Test: `src/test/kotlin/com/safelense/analysis/AnalysisCaseControllerTests.kt`

**Interfaces:**
- Consumes: 템플릿 카탈로그와 Task 2 저장소.
- Produces: `create(userId, command)`, `get(userId, caseId)`.
- Produces: `POST /api/v1/analysis-cases`, `GET /api/v1/analysis-cases/{caseId}`.

- [ ] **Step 1: 서비스 실패 테스트 작성**

다음 경우를 `AnalysisCaseServiceTests.kt`에 구체적으로 작성한다.

```kotlin
@Test
fun `creates a case only for the users property`() {
    `when`(propertyRepository.findByIdAndUserId(3L, 7L)).thenReturn(property())
    `when`(caseRepository.save(any(AnalysisCase::class.java))).thenAnswer {
        (it.arguments[0] as AnalysisCase).apply { id = 11L }
    }

    val result = service.create(
        userId = 7L,
        command = AnalysisCaseCreateCommand(AnalysisStage.BEFORE_CONTRACT, 3L),
    )

    assertThat(result.id).isEqualTo(11L)
    assertThat(result.templateVersion).isEqualTo(ANALYSIS_TEMPLATE_VERSION)
}

@Test
fun `hides a property not owned by the user`() {
    `when`(propertyRepository.findByIdAndUserId(3L, 7L)).thenReturn(null)

    assertThatThrownBy {
        service.create(7L, AnalysisCaseCreateCommand(AnalysisStage.BEFORE_CONTRACT, 3L))
    }.isInstanceOf(HomePropertyNotFoundException::class.java)
}

@Test
fun `returns six empty document slots and saved answers`() {
    `when`(caseRepository.findByIdAndUserId(11L, 7L)).thenReturn(analysisCase())
    `when`(documentRepository.findAllByCaseId(11L)).thenReturn(emptyList())
    `when`(answerRepository.findAllByCaseId(11L)).thenReturn(emptyList())

    val result = service.get(7L, 11L)

    assertThat(result.documents).hasSize(6)
    assertThat(result.uploadedCount).isZero()
    assertThat(result.answers).isEmpty()
}

private fun property(): HomeProperty =
    HomeProperty(
        id = 3L,
        userId = 7L,
        address = "서울시 마포구 합정동 123-45",
        depositAmount = 25000L,
        buildingType = BuildingType.MULTI_FAMILY,
        landlordName = "홍길동",
        plannedContractDate = LocalDate.parse("2026-08-01"),
    )

private fun analysisCase(): AnalysisCase =
    AnalysisCase(
        id = 11L,
        userId = 7L,
        propertyId = 3L,
        stage = AnalysisStage.BEFORE_CONTRACT,
        templateVersion = ANALYSIS_TEMPLATE_VERSION,
    )
```

- [ ] **Step 2: 서비스 테스트 실패 확인**

Run:

```bash
./gradlew test --tests 'com.safelense.analysis.AnalysisCaseServiceTests'
```

Expected: 서비스와 명령·응답 타입 부재로 컴파일 실패한다.

- [ ] **Step 3: 케이스 예외와 서비스 구현**

`AnalysisExceptions.kt`에 다음 예외를 정의한다.

```kotlin
// 분석 케이스 입력 API의 도메인 오류를 구분하는 예외
package com.safelense.analysis

class AnalysisCaseNotFoundException : RuntimeException()
class InvalidAnalysisDocumentException : RuntimeException()
class AnalysisDocumentTooLargeException : RuntimeException()
class AnalysisDocumentNotFoundException : RuntimeException()
class InvalidAnalysisChecklistException : RuntimeException()
```

`AnalysisCaseService.kt`에 다음 계약을 구현한다.

```kotlin
data class AnalysisCaseCreateCommand(
    val stage: AnalysisStage,
    val propertyId: Long,
)

data class AnalysisCaseCreated(
    val id: Long,
    val propertyId: Long,
    val stage: AnalysisStage,
    val templateVersion: String,
)

data class AnalysisDocumentSlotView(
    val documentType: String,
    val label: String,
    val required: Boolean,
    val documentId: Long?,
    val originalFileName: String?,
    val mimeType: String?,
    val fileSize: Long?,
)

data class AnalysisChecklistAnswerView(
    val itemKey: String,
    val checked: Boolean,
)

data class AnalysisCaseView(
    val id: Long,
    val propertyId: Long,
    val stage: AnalysisStage,
    val templateVersion: String,
    val documents: List<AnalysisDocumentSlotView>,
    val uploadedCount: Int,
    val answers: List<AnalysisChecklistAnswerView>,
)
```

서비스의 `create`와 `get`은 다음 흐름을 사용한다.

```kotlin
@Service
class AnalysisCaseService(
    private val propertyRepository: HomePropertyRepository,
    private val caseRepository: AnalysisCaseRepository,
    private val documentRepository: AnalysisDocumentRepository,
    private val answerRepository: AnalysisChecklistAnswerRepository,
    private val catalog: AnalysisTemplateCatalog,
) {
    @Transactional
    fun create(userId: Long, command: AnalysisCaseCreateCommand): AnalysisCaseCreated {
        propertyRepository.findByIdAndUserId(command.propertyId, userId)
            ?: throw HomePropertyNotFoundException()
        val template = catalog.get(command.stage)
        val saved = caseRepository.save(
            AnalysisCase(
                userId = userId,
                propertyId = command.propertyId,
                stage = command.stage,
                templateVersion = template.version,
            ),
        )
        return AnalysisCaseCreated(
            id = requireNotNull(saved.id),
            propertyId = saved.propertyId,
            stage = saved.stage,
            templateVersion = saved.templateVersion,
        )
    }

    @Transactional(readOnly = true)
    fun get(userId: Long, caseId: Long): AnalysisCaseView {
        val analysisCase = caseRepository.findByIdAndUserId(caseId, userId)
            ?: throw AnalysisCaseNotFoundException()
        val template = catalog.get(analysisCase.stage)
        val documents = documentRepository.findAllByCaseId(caseId).associateBy { it.documentType }
        val answers = answerRepository.findAllByCaseId(caseId).associateBy { it.itemKey }
        return AnalysisCaseView(
            id = requireNotNull(analysisCase.id),
            propertyId = analysisCase.propertyId,
            stage = analysisCase.stage,
            templateVersion = analysisCase.templateVersion,
            documents = template.documents.map { slot ->
                val document = documents[slot.documentType]
                AnalysisDocumentSlotView(
                    documentType = slot.documentType,
                    label = slot.label,
                    required = slot.required,
                    documentId = document?.id,
                    originalFileName = document?.originalFileName,
                    mimeType = document?.mimeType,
                    fileSize = document?.fileSize,
                )
            },
            uploadedCount = documents.size,
            answers = catalog.itemKeys(analysisCase.stage).mapNotNull { itemKey ->
                answers[itemKey]?.let { AnalysisChecklistAnswerView(it.itemKey, it.checked) }
            },
        )
    }
}
```

- [ ] **Step 4: 서비스 테스트 통과 확인**

Run:

```bash
./gradlew test --tests 'com.safelense.analysis.AnalysisCaseServiceTests'
```

Expected: PASS.

- [ ] **Step 5: 케이스 MVC 실패 테스트 작성**

`AnalysisCaseControllerTests.kt`에 다음 HTTP 계약을 작성한다.

```kotlin
@Test
fun `creates an analysis case`() {
    val created = AnalysisCaseCreated(11L, 3L, AnalysisStage.BEFORE_CONTRACT, ANALYSIS_TEMPLATE_VERSION)
    `when`(service.create(7L, AnalysisCaseCreateCommand(AnalysisStage.BEFORE_CONTRACT, 3L)))
        .thenReturn(created)

    mockMvc.perform(
        post("/api/v1/analysis-cases")
            .principal(UsernamePasswordAuthenticationToken(7L, null))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"stage":"BEFORE_CONTRACT","propertyId":3}"""),
    )
        .andExpect(status().isCreated)
        .andExpect(jsonPath("$.id").value(11))
        .andExpect(jsonPath("$.stage").value("BEFORE_CONTRACT"))
}

@Test
fun `gets an owned analysis case`() {
    `when`(service.get(7L, 11L)).thenReturn(caseView())

    mockMvc.perform(
        get("/api/v1/analysis-cases/11")
            .principal(UsernamePasswordAuthenticationToken(7L, null)),
    )
        .andExpect(status().isOk)
        .andExpect(jsonPath("$.documents.length()").value(6))
        .andExpect(jsonPath("$.uploadedCount").value(0))
}

private fun caseView(): AnalysisCaseView {
    val template = AnalysisTemplateCatalog().get(AnalysisStage.BEFORE_CONTRACT)
    return AnalysisCaseView(
        id = 11L,
        propertyId = 3L,
        stage = AnalysisStage.BEFORE_CONTRACT,
        templateVersion = ANALYSIS_TEMPLATE_VERSION,
        documents = template.documents.map {
            AnalysisDocumentSlotView(
                documentType = it.documentType,
                label = it.label,
                required = it.required,
                documentId = null,
                originalFileName = null,
                mimeType = null,
                fileSize = null,
            )
        },
        uploadedCount = 0,
        answers = emptyList(),
    )
}
```

잘못된 단계는 `INVALID_STAGE`, 다른 사용자 케이스는 `ANALYSIS_CASE_NOT_FOUND`인지 각각 검증한다.

- [ ] **Step 6: 케이스 컨트롤러와 오류 매핑 구현**

`AnalysisCaseController.kt`는 다음 계약으로 인증 principal의 `Long` 사용자 ID를 서비스에 전달한다.

```kotlin
// 인증 사용자의 분석 케이스 생성과 입력 상태 조회 API를 제공하는 컨트롤러
package com.safelense.analysis

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

data class AnalysisCaseCreateRequest(
    @field:NotBlank
    val stage: String,
    @field:Positive
    val propertyId: Long,
)

@RestController
@RequestMapping("/api/v1/analysis-cases")
class AnalysisCaseController(
    private val service: AnalysisCaseService,
    private val catalog: AnalysisTemplateCatalog,
) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        authentication: Authentication,
        @Valid @RequestBody request: AnalysisCaseCreateRequest,
    ): AnalysisCaseCreated =
        service.create(
            authentication.principal as Long,
            AnalysisCaseCreateCommand(catalog.parse(request.stage), request.propertyId),
        )

    @GetMapping("/{caseId}")
    fun get(
        authentication: Authentication,
        @PathVariable caseId: Long,
    ): AnalysisCaseView =
        service.get(authentication.principal as Long, caseId)
}
```

`ApiExceptionHandler.kt`에 다음 매핑을 추가한다.

```kotlin
@ExceptionHandler(AnalysisCaseNotFoundException::class)
fun handleAnalysisCaseNotFound(): ResponseEntity<ApiError> =
    error(HttpStatus.NOT_FOUND, "ANALYSIS_CASE_NOT_FOUND", "Analysis case was not found.")
```

- [ ] **Step 7: 케이스 API 테스트 통과 확인**

Run:

```bash
./gradlew test --tests 'com.safelense.analysis.AnalysisCase*'
```

Expected: PASS.

- [ ] **Step 8: 케이스 API 커밋**

```bash
git add src/main/kotlin/com/safelense/analysis/AnalysisExceptions.kt src/main/kotlin/com/safelense/analysis/AnalysisCaseService.kt src/main/kotlin/com/safelense/analysis/AnalysisCaseController.kt src/main/kotlin/com/safelense/auth/presentation/ApiExceptionHandler.kt src/test/kotlin/com/safelense/analysis/AnalysisCaseServiceTests.kt src/test/kotlin/com/safelense/analysis/AnalysisCaseControllerTests.kt
git commit -m "feat: 분석 케이스 생성 조회 API 추가"
```

### Task 4: 분석 문서 업로드·삭제 API

**Files:**
- Create: `src/main/kotlin/com/safelense/analysis/AnalysisDocumentService.kt`
- Create: `src/main/kotlin/com/safelense/analysis/AnalysisDocumentController.kt`
- Modify: `src/main/resources/application.yml`
- Modify: `src/main/kotlin/com/safelense/auth/presentation/ApiExceptionHandler.kt`
- Test: `src/test/kotlin/com/safelense/analysis/AnalysisDocumentServiceTests.kt`
- Test: `src/test/kotlin/com/safelense/analysis/AnalysisDocumentControllerTests.kt`

**Interfaces:**
- Consumes: `findByIdAndUserIdForUpdate`, 템플릿의 문서 종류.
- Produces: `upload(userId, caseId, documentType, file)`, `delete(userId, caseId, documentId)`.
- Produces: 문서 POST와 DELETE API.

- [ ] **Step 1: 문서 서비스 실패 테스트 작성**

서비스 테스트에서 다음을 검증한다.

```kotlin
@Test
fun `uploads a document into an empty slot`() {
    `when`(caseRepository.findByIdAndUserIdForUpdate(11L, 7L)).thenReturn(analysisCase())
    `when`(documentRepository.findByCaseIdAndDocumentType(11L, "REGISTRY_CERTIFICATE")).thenReturn(null)
    `when`(documentRepository.save(any(AnalysisDocument::class.java))).thenAnswer {
        (it.arguments[0] as AnalysisDocument).apply { id = 21L }
    }
    `when`(documentRepository.countByCaseId(11L)).thenReturn(1L)
    val file = MockMultipartFile("file", "registry.pdf", "application/pdf", "pdf".toByteArray())

    val result = service.upload(7L, 11L, "REGISTRY_CERTIFICATE", file)

    assertThat(result.document.id).isEqualTo(21L)
    assertThat(result.uploadedCount).isEqualTo(1)
}

@Test
fun `replaces a document in the same slot`() {
    val existing = document()
    `when`(caseRepository.findByIdAndUserIdForUpdate(11L, 7L)).thenReturn(analysisCase())
    `when`(documentRepository.findByCaseIdAndDocumentType(11L, "REGISTRY_CERTIFICATE")).thenReturn(existing)
    `when`(documentRepository.save(existing)).thenReturn(existing)
    `when`(documentRepository.countByCaseId(11L)).thenReturn(1L)
    val file = MockMultipartFile("file", "new.pdf", "application/pdf", "new".toByteArray())

    service.upload(7L, 11L, "REGISTRY_CERTIFICATE", file)

    assertThat(existing.originalFileName).isEqualTo("new.pdf")
    assertThat(existing.content).containsExactly("new".toByteArray())
}

@Test
fun `rejects an unsupported or oversized document`() {
    `when`(caseRepository.findByIdAndUserIdForUpdate(11L, 7L)).thenReturn(analysisCase())

    assertThatThrownBy {
        service.upload(
            7L,
            11L,
            "REGISTRY_CERTIFICATE",
            MockMultipartFile("file", "bad.txt", "text/plain", "bad".toByteArray()),
        )
    }.isInstanceOf(InvalidAnalysisDocumentException::class.java)

    assertThatThrownBy {
        service.upload(
            7L,
            11L,
            "REGISTRY_CERTIFICATE",
            MockMultipartFile("file", "big.pdf", "application/pdf", ByteArray(10 * 1024 * 1024 + 1)),
        )
    }.isInstanceOf(AnalysisDocumentTooLargeException::class.java)
}

private fun analysisCase(): AnalysisCase =
    AnalysisCase(
        id = 11L,
        userId = 7L,
        propertyId = 3L,
        stage = AnalysisStage.BEFORE_CONTRACT,
        templateVersion = ANALYSIS_TEMPLATE_VERSION,
    )

private fun document(): AnalysisDocument =
    AnalysisDocument(
        id = 21L,
        caseId = 11L,
        documentType = "REGISTRY_CERTIFICATE",
        originalFileName = "registry.pdf",
        mimeType = "application/pdf",
        fileSize = 3L,
        content = "pdf".toByteArray(),
    )
```

삭제 테스트는 다른 사용자 케이스가 `AnalysisCaseNotFoundException`, 다른 케이스 소속 문서가 `AnalysisDocumentNotFoundException`, 정상 문서가 `repository.delete(document)` 호출인지 검증한다.

- [ ] **Step 2: 문서 서비스 테스트 실패 확인**

Run:

```bash
./gradlew test --tests 'com.safelense.analysis.AnalysisDocumentServiceTests'
```

Expected: 문서 서비스 타입 부재로 컴파일 실패한다.

- [ ] **Step 3: 문서 서비스 구현**

`AnalysisDocumentService.kt`에 다음 상수를 둔다.

```kotlin
private const val MAX_DOCUMENT_SIZE = 10L * 1024 * 1024
private val ALLOWED_DOCUMENT_TYPES = setOf("application/pdf", "image/jpeg", "image/png")
```

`upload`와 `delete`는 먼저 케이스 행을 잠그고 다음 구현으로 슬롯을 생성·교체한다.

응답 타입은 다음과 같다.

```kotlin
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
```

- [ ] **Step 4: 문서 서비스 테스트 통과 확인**

Run:

```bash
./gradlew test --tests 'com.safelense.analysis.AnalysisDocumentServiceTests'
```

Expected: PASS.

- [ ] **Step 5: multipart MVC 실패 테스트 작성**

`AnalysisDocumentControllerTests.kt`에서 `multipart("/api/v1/analysis-cases/11/documents")`에 PDF 파일과 `documentType` 파트를 보내 성공 응답을 검증한다. DELETE 성공은 `204`, 서비스가 각 문서 예외를 던질 때 `400`, `404`, `413`을 검증한다.

- [ ] **Step 6: 문서 컨트롤러·업로드 제한·오류 매핑 구현**

`AnalysisDocumentController.kt`는 POST multipart와 DELETE를 제공한다.

```kotlin
// 인증 사용자의 분석 서류 업로드·삭제 API를 제공하는 컨트롤러
package com.safelense.analysis

import org.springframework.http.MediaType
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import org.springframework.http.HttpStatus

@RestController
@RequestMapping("/api/v1/analysis-cases/{caseId}/documents")
class AnalysisDocumentController(
    private val service: AnalysisDocumentService,
) {
    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun upload(
        authentication: Authentication,
        @PathVariable caseId: Long,
        @RequestParam documentType: String,
        @RequestParam file: MultipartFile,
    ): AnalysisDocumentUploadResult =
        service.upload(authentication.principal as Long, caseId, documentType, file)

    @DeleteMapping("/{documentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        authentication: Authentication,
        @PathVariable caseId: Long,
        @PathVariable documentId: Long,
    ) {
        service.delete(authentication.principal as Long, caseId, documentId)
    }
}
```

`application.yml`에 다음 설정을 추가한다.

```yaml
  servlet:
    multipart:
      max-file-size: 10MB
      max-request-size: 11MB
```

`ApiExceptionHandler.kt`에 다음 매핑을 추가한다.

```kotlin
@ExceptionHandler(InvalidAnalysisDocumentException::class)
fun handleInvalidAnalysisDocument(): ResponseEntity<ApiError> =
    error(HttpStatus.BAD_REQUEST, "INVALID_DOCUMENT", "Document is invalid.")

@ExceptionHandler(AnalysisDocumentTooLargeException::class, MaxUploadSizeExceededException::class)
fun handleAnalysisDocumentTooLarge(): ResponseEntity<ApiError> =
    error(HttpStatus.PAYLOAD_TOO_LARGE, "DOCUMENT_TOO_LARGE", "Document is too large.")

@ExceptionHandler(AnalysisDocumentNotFoundException::class)
fun handleAnalysisDocumentNotFound(): ResponseEntity<ApiError> =
    error(HttpStatus.NOT_FOUND, "ANALYSIS_DOCUMENT_NOT_FOUND", "Analysis document was not found.")
```

- [ ] **Step 7: 문서 API 테스트 통과 확인**

Run:

```bash
./gradlew test --tests 'com.safelense.analysis.AnalysisDocument*'
```

Expected: PASS.

- [ ] **Step 8: 문서 API 커밋**

```bash
git add src/main/kotlin/com/safelense/analysis/AnalysisDocumentService.kt src/main/kotlin/com/safelense/analysis/AnalysisDocumentController.kt src/main/resources/application.yml src/main/kotlin/com/safelense/auth/presentation/ApiExceptionHandler.kt src/test/kotlin/com/safelense/analysis/AnalysisDocumentServiceTests.kt src/test/kotlin/com/safelense/analysis/AnalysisDocumentControllerTests.kt
git commit -m "feat: 분석 서류 업로드 삭제 API 추가"
```

### Task 5: 체크리스트 전체 교체 API

**Files:**
- Create: `src/main/kotlin/com/safelense/analysis/AnalysisChecklistService.kt`
- Create: `src/main/kotlin/com/safelense/analysis/AnalysisChecklistController.kt`
- Modify: `src/main/kotlin/com/safelense/auth/presentation/ApiExceptionHandler.kt`
- Test: `src/test/kotlin/com/safelense/analysis/AnalysisChecklistServiceTests.kt`
- Test: `src/test/kotlin/com/safelense/analysis/AnalysisChecklistControllerTests.kt`

**Interfaces:**
- Consumes: 케이스 잠금, 템플릿의 단계별 `itemKeys`.
- Produces: `replace(userId, caseId, answers)`.
- Produces: `PUT /api/v1/analysis-cases/{caseId}/checklist`.

- [ ] **Step 1: 체크리스트 서비스 실패 테스트 작성**

```kotlin
@Test
fun `replaces all answers including an empty set`() {
    `when`(caseRepository.findByIdAndUserIdForUpdate(11L, 7L)).thenReturn(analysisCase())
    `when`(
        answerRepository.saveAll(
            org.mockito.ArgumentMatchers.anyList<AnalysisChecklistAnswer>(),
        ),
    ).thenAnswer {
        @Suppress("UNCHECKED_CAST")
        it.arguments[0] as List<AnalysisChecklistAnswer>
    }

    val result = service.replace(
        userId = 7L,
        caseId = 11L,
        answers = listOf(
            AnalysisChecklistAnswerCommand("VISITED_PROPERTY", true),
            AnalysisChecklistAnswerCommand("CHECKED_INTERIOR", false),
        ),
    )

    verify(answerRepository).deleteAllByCaseId(11L)
    verify(answerRepository).saveAll(
        org.mockito.ArgumentMatchers.anyList<AnalysisChecklistAnswer>(),
    )
    assertThat(result.map { it.itemKey })
        .containsExactly("VISITED_PROPERTY", "CHECKED_INTERIOR")

    service.replace(7L, 11L, emptyList())
    verify(answerRepository, times(2)).deleteAllByCaseId(11L)
}

@Test
fun `rejects unknown and duplicate item keys`() {
    `when`(caseRepository.findByIdAndUserIdForUpdate(11L, 7L)).thenReturn(analysisCase())

    assertThatThrownBy {
        service.replace(7L, 11L, listOf(AnalysisChecklistAnswerCommand("UNKNOWN", true)))
    }.isInstanceOf(InvalidAnalysisChecklistException::class.java)

    assertThatThrownBy {
        service.replace(
            7L,
            11L,
            listOf(
                AnalysisChecklistAnswerCommand("VISITED_PROPERTY", true),
                AnalysisChecklistAnswerCommand("VISITED_PROPERTY", false),
            ),
        )
    }.isInstanceOf(InvalidAnalysisChecklistException::class.java)
}

private fun analysisCase(): AnalysisCase =
    AnalysisCase(
        id = 11L,
        userId = 7L,
        propertyId = 3L,
        stage = AnalysisStage.BEFORE_CONTRACT,
        templateVersion = ANALYSIS_TEMPLATE_VERSION,
    )
```

- [ ] **Step 2: 체크리스트 서비스 테스트 실패 확인**

Run:

```bash
./gradlew test --tests 'com.safelense.analysis.AnalysisChecklistServiceTests'
```

Expected: 체크리스트 서비스 타입 부재로 컴파일 실패한다.

- [ ] **Step 3: 체크리스트 서비스 구현**

다음 명령 타입과 메서드를 구현한다.

```kotlin
data class AnalysisChecklistAnswerCommand(
    val itemKey: String,
    val checked: Boolean,
)

@Transactional
fun replace(
    userId: Long,
    caseId: Long,
    answers: List<AnalysisChecklistAnswerCommand>,
): List<AnalysisChecklistAnswerView>
```

구현은 다음 순서로 케이스 잠금, 키 검증, 전체 교체, 템플릿 순서 정렬을 수행한다.

```kotlin
@Service
class AnalysisChecklistService(
    private val caseRepository: AnalysisCaseRepository,
    private val answerRepository: AnalysisChecklistAnswerRepository,
    private val catalog: AnalysisTemplateCatalog,
) {
    @Transactional
    fun replace(
        userId: Long,
        caseId: Long,
        answers: List<AnalysisChecklistAnswerCommand>,
    ): List<AnalysisChecklistAnswerView> {
        val analysisCase = caseRepository.findByIdAndUserIdForUpdate(caseId, userId)
            ?: throw AnalysisCaseNotFoundException()
        val requestKeys = answers.map { it.itemKey }
        val allowedKeys = catalog.itemKeys(analysisCase.stage)
        if (requestKeys.distinct().size != requestKeys.size || requestKeys.any { it !in allowedKeys }) {
            throw InvalidAnalysisChecklistException()
        }

        answerRepository.deleteAllByCaseId(caseId)
        val savedByKey = if (answers.isEmpty()) {
            emptyMap()
        } else {
            answerRepository.saveAll(
                answers.map {
                    AnalysisChecklistAnswer(
                        caseId = caseId,
                        itemKey = it.itemKey,
                        checked = it.checked,
                    )
                },
            ).associateBy { it.itemKey }
        }
        return allowedKeys.mapNotNull { itemKey ->
            savedByKey[itemKey]?.let { AnalysisChecklistAnswerView(it.itemKey, it.checked) }
        }
    }
}
```

- [ ] **Step 4: 체크리스트 서비스 테스트 통과 확인**

Run:

```bash
./gradlew test --tests 'com.safelense.analysis.AnalysisChecklistServiceTests'
```

Expected: PASS.

- [ ] **Step 5: 체크리스트 MVC 실패 테스트 작성**

```kotlin
@Test
fun `replaces partial checklist answers`() {
    val command = listOf(AnalysisChecklistAnswerCommand("VISITED_PROPERTY", true))
    `when`(service.replace(7L, 11L, command))
        .thenReturn(listOf(AnalysisChecklistAnswerView("VISITED_PROPERTY", true)))

    mockMvc.perform(
        put("/api/v1/analysis-cases/11/checklist")
            .principal(UsernamePasswordAuthenticationToken(7L, null))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"answers":[{"itemKey":"VISITED_PROPERTY","checked":true}]}"""),
    )
        .andExpect(status().isOk)
        .andExpect(jsonPath("$.answers[0].itemKey").value("VISITED_PROPERTY"))
        .andExpect(jsonPath("$.answers[0].checked").value(true))
}

@Test
fun `accepts an empty checklist`() {
    `when`(service.replace(7L, 11L, emptyList())).thenReturn(emptyList())

    mockMvc.perform(
        put("/api/v1/analysis-cases/11/checklist")
            .principal(UsernamePasswordAuthenticationToken(7L, null))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"answers":[]}"""),
    )
        .andExpect(status().isOk)
        .andExpect(jsonPath("$.answers").isEmpty())
}
```

- [ ] **Step 6: 체크리스트 컨트롤러와 오류 매핑 구현**

요청·응답 타입은 다음 계약을 사용한다.

```kotlin
data class AnalysisChecklistAnswerRequest(
    @field:NotBlank
    val itemKey: String,
    val checked: Boolean,
)

data class AnalysisChecklistReplaceRequest(
    @field:Valid
    val answers: List<AnalysisChecklistAnswerRequest>,
)

data class AnalysisChecklistEnvelope(
    val answers: List<AnalysisChecklistAnswerView>,
)

@RestController
@RequestMapping("/api/v1/analysis-cases/{caseId}/checklist")
class AnalysisChecklistController(
    private val service: AnalysisChecklistService,
) {
    @PutMapping
    fun replace(
        authentication: Authentication,
        @PathVariable caseId: Long,
        @Valid @RequestBody request: AnalysisChecklistReplaceRequest,
    ): AnalysisChecklistEnvelope =
        AnalysisChecklistEnvelope(
            service.replace(
                authentication.principal as Long,
                caseId,
                request.answers.map { AnalysisChecklistAnswerCommand(it.itemKey, it.checked) },
            ),
        )
}
```

`ApiExceptionHandler.kt`에 다음 매핑을 추가한다.

```kotlin
@ExceptionHandler(InvalidAnalysisChecklistException::class)
fun handleInvalidAnalysisChecklist(): ResponseEntity<ApiError> =
    error(HttpStatus.BAD_REQUEST, "INVALID_CHECKLIST", "Checklist is invalid.")
```

- [ ] **Step 7: 체크리스트 API 테스트 통과 확인**

Run:

```bash
./gradlew test --tests 'com.safelense.analysis.AnalysisChecklist*'
```

Expected: PASS.

- [ ] **Step 8: 체크리스트 API 커밋**

```bash
git add src/main/kotlin/com/safelense/analysis/AnalysisChecklistService.kt src/main/kotlin/com/safelense/analysis/AnalysisChecklistController.kt src/main/kotlin/com/safelense/auth/presentation/ApiExceptionHandler.kt src/test/kotlin/com/safelense/analysis/AnalysisChecklistServiceTests.kt src/test/kotlin/com/safelense/analysis/AnalysisChecklistControllerTests.kt
git commit -m "feat: 분석 체크리스트 저장 API 추가"
```

### Task 6: 전체 검증과 작업 기록

**Files:**
- Modify: `docs/work-notes/checklist.md`
- Modify: `docs/work-notes/context-notes.md`

**Interfaces:**
- Consumes: Task 1~5의 모든 API와 테스트.
- Produces: 검증 결과와 완료 작업 기록.

- [ ] **Step 1: 분석 패키지 테스트 실행**

Run:

```bash
./gradlew test --tests 'com.safelense.analysis.*'
```

Expected: 모든 분석 입력 API 테스트 PASS.

- [ ] **Step 2: 전체 테스트 실행**

Run:

```bash
./gradlew test
```

Expected: 기존 인증·주택 테스트를 포함해 전체 PASS.

- [ ] **Step 3: 실행 JAR 생성**

Run:

```bash
./gradlew bootJar
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: 작업 문서 완료 처리**

`docs/work-notes/checklist.md`에서 마이그레이션, 템플릿·케이스, 문서, 체크리스트, 전체 검증 항목을 완료 처리한다. `docs/work-notes/context-notes.md`에는 실제 구현된 템플릿 버전, V4 테이블, 파일 제한, 실행한 검증 명령과 결과를 기록한다.

- [ ] **Step 5: 변경 품질 확인**

Run:

```bash
git diff --check
git status --short
```

Expected: 공백 오류가 없고 의도한 분석 입력 API 파일과 작업 문서만 표시된다.

- [ ] **Step 6: 작업 기록 커밋**

```bash
git add docs/work-notes/checklist.md docs/work-notes/context-notes.md
git commit -m "docs: 분석 케이스 입력 API 작업 결과 기록"
```
