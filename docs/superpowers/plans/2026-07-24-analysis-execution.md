# Analysis Execution Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 외부 AI 모델 없이 `dive-2026-v1` 규칙으로 분석 케이스 결과를 멱등 생성하는 API를 구현한다.

**Architecture:** `AnalysisRiskRuleEngine`은 영속성에 의존하지 않는 순수 규칙 계산을 담당한다. `AnalysisExecutionService`는 사용자 소유 케이스 잠금, 입력 조립·스냅샷, 멱등성, 결과 저장을 담당하고 `AnalysisExecutionController`가 HTTP 상태와 요청 검증을 담당한다.

**Tech Stack:** Kotlin 2.3.10, Spring Boot 4.1.0, Spring MVC, Spring Data JPA, MySQL, Flyway, JUnit 5, AssertJ, Mockito.

## Global Constraints

- 원본 CSV·XLSX와 외부 AI·공공 데이터 API를 런타임에서 읽지 않는다.
- 점수는 사고 확률이 아니라 위험 신호 누적 강도다.
- 금액 요청 필드 단위는 만원이다.
- 누락값을 안전으로 계산하지 않고 핵심 근거가 부족하면 `UNKNOWN`을 반환한다.
- 새 소스 파일 첫 줄에 역할을 설명하는 한 줄 한국어 주석을 둔다.
- 사용자의 요청에 따라 모든 변경은 최종 검증 뒤 커밋 하나로 저장한다.

---

### Task 1: 결과 감사 필드 마이그레이션

**Files:**
- Create: `src/main/resources/db/migration/V7__add_analysis_execution_audit.sql`
- Modify: `src/main/kotlin/com/safelense/analysis/AnalysisResult.kt`
- Modify: `src/main/kotlin/com/safelense/analysis/AnalysisResultRepository.kt`
- Test: `src/test/kotlin/com/safelense/analysis/AnalysisExecutionMigrationTests.kt`
- Test: `src/test/kotlin/com/safelense/analysis/AnalysisResultRepositoryContractTests.kt`

**Interfaces:**
- Consumes: 기존 `analysis_results`와 케이스별 유일 제약.
- Produces: `AnalysisResult.idempotencyKey`, `AnalysisResult.inputSnapshot`, `AnalysisResultRepository.findByCaseId`, `existsByCaseId`.

- [x] **Step 1: 실패하는 마이그레이션·저장소 계약 테스트를 작성한다.**

```kotlin
assertThat(sql).contains("ADD COLUMN idempotency_key VARCHAR(100) NULL")
assertThat(sql).contains("ADD COLUMN input_snapshot TEXT NULL")
assertThat(methodNames).contains("findByCaseId", "existsByCaseId")
```

- [x] **Step 2: 테스트를 실행해 V7과 저장소 메서드 부재로 실패하는지 확인한다.**

Run: `./gradlew test --tests 'com.safelense.analysis.AnalysisExecutionMigrationTests' --tests 'com.safelense.analysis.AnalysisResultRepositoryContractTests'`

Expected: V7 파일 또는 저장소 메서드 부재로 `FAILED`.

- [x] **Step 3: 최소 마이그레이션과 엔티티·저장소 필드를 구현한다.**

```sql
ALTER TABLE analysis_results
    ADD COLUMN idempotency_key VARCHAR(100) NULL,
    ADD COLUMN input_snapshot TEXT NULL;
```

```kotlin
fun findByCaseId(caseId: Long): AnalysisResult?
fun existsByCaseId(caseId: Long): Boolean
```

- [x] **Step 4: 집중 테스트가 통과하는지 확인한다.**

Run: `./gradlew test --tests 'com.safelense.analysis.AnalysisExecutionMigrationTests' --tests 'com.safelense.analysis.AnalysisResultRepositoryContractTests'`

Expected: `BUILD SUCCESSFUL`.

### Task 2: 버전형 위험 규칙 엔진

**Files:**
- Create: `src/main/kotlin/com/safelense/analysis/AnalysisRiskRuleEngine.kt`
- Create: `src/test/kotlin/com/safelense/analysis/AnalysisRiskRuleEngineTests.kt`

**Interfaces:**
- Consumes: `AnalysisStage`, 체크리스트 답변과 구조화 위험 사실.
- Produces: `AnalysisRiskInput`, `AnalysisRiskAssessment`, `AnalysisRiskRuleEngine.assess`.

- [x] **Step 1: 입력 없음, 비율 경계, 고위험 우선, 낮은 충족률, 사후 절차의 실패 테스트를 작성한다.**

```kotlin
assertThat(engine.assess(emptyInput()).grade).isEqualTo(AnalysisRiskGrade.UNKNOWN)
assertThat(engine.assess(completeInput(90_000L, 100_000L)).score).isEqualTo(55)
assertThat(engine.assess(ownershipMismatchInput()).grade).isEqualTo(AnalysisRiskGrade.HIGH)
```

- [x] **Step 2: 규칙 엔진 테스트를 실행해 타입 부재로 컴파일 실패하는지 확인한다.**

Run: `./gradlew test --tests 'com.safelense.analysis.AnalysisRiskRuleEngineTests'`

Expected: `AnalysisRiskRuleEngine`과 입력 타입 부재로 `FAILED`.

- [x] **Step 3: 설계 문서의 점수·신뢰도·UNKNOWN 규칙을 최소 순수 함수로 구현한다.**

```kotlin
const val ANALYSIS_RULE_VERSION = "dive-2026-v1"

class AnalysisRiskRuleEngine {
    fun assess(input: AnalysisRiskInput): AnalysisRiskAssessment
}
```

- [x] **Step 4: 규칙 엔진 집중 테스트가 통과하는지 확인한다.**

Run: `./gradlew test --tests 'com.safelense.analysis.AnalysisRiskRuleEngineTests'`

Expected: `BUILD SUCCESSFUL`.

### Task 3: 분석 실행 서비스와 HTTP API

**Files:**
- Create: `src/main/kotlin/com/safelense/analysis/AnalysisExecutionService.kt`
- Create: `src/main/kotlin/com/safelense/analysis/AnalysisExecutionController.kt`
- Modify: `src/main/kotlin/com/safelense/analysis/AnalysisResultService.kt`
- Modify: `src/main/kotlin/com/safelense/analysis/AnalysisExceptions.kt`
- Modify: `src/main/kotlin/com/safelense/auth/presentation/ApiExceptionHandler.kt`
- Create: `src/test/kotlin/com/safelense/analysis/AnalysisExecutionServiceTests.kt`
- Create: `src/test/kotlin/com/safelense/analysis/AnalysisExecutionControllerTests.kt`

**Interfaces:**
- Consumes: `AnalysisRiskRuleEngine.assess`, 케이스·주택·문서·답변·결과 저장소.
- Produces: `AnalysisExecutionService.analyze`, `POST /api/v1/analysis-cases/{caseId}/analyze`.

- [x] **Step 1: 최초 저장, 스냅샷, 사용자 격리, 동일 키 멱등, 다른 키 충돌 서비스 테스트를 작성한다.**

```kotlin
val outcome = service.analyze(7L, 11L, "request-1", command)
assertThat(outcome.created).isTrue()
assertThat(outcome.result.ruleVersion).isEqualTo(ANALYSIS_RULE_VERSION)
```

- [x] **Step 2: 서비스 테스트를 실행해 실행 서비스 부재로 실패하는지 확인한다.**

Run: `./gradlew test --tests 'com.safelense.analysis.AnalysisExecutionServiceTests'`

Expected: 실행 서비스 타입 부재로 `FAILED`.

- [x] **Step 3: 케이스 잠금부터 결과 저장까지 최소 서비스 구현을 추가한다.**

```kotlin
fun analyze(
    userId: Long,
    caseId: Long,
    idempotencyKey: String,
    command: AnalysisExecutionCommand,
): AnalysisExecutionOutcome
```

- [x] **Step 4: 서비스 테스트가 통과하는지 확인한다.**

Run: `./gradlew test --tests 'com.safelense.analysis.AnalysisExecutionServiceTests'`

Expected: `BUILD SUCCESSFUL`.

- [x] **Step 5: 201·200·400·404·409 MVC 실패 테스트를 작성한다.**

```kotlin
post("/api/v1/analysis-cases/11/analyze")
    .header("Idempotency-Key", "request-1")
    .contentType(MediaType.APPLICATION_JSON)
```

- [x] **Step 6: MVC 테스트를 실행해 컨트롤러 부재로 실패하는지 확인한다.**

Run: `./gradlew test --tests 'com.safelense.analysis.AnalysisExecutionControllerTests'`

Expected: 실행 컨트롤러 부재로 `FAILED`.

- [x] **Step 7: 요청 enum 파싱, 검증, 상태·Location 응답과 오류 매핑을 구현한다.**

```kotlin
@PostMapping("/{caseId}/analyze")
fun analyze(...): ResponseEntity<AnalysisResultDetail>
```

- [x] **Step 8: 실행 API 집중 테스트가 통과하는지 확인한다.**

Run: `./gradlew test --tests 'com.safelense.analysis.AnalysisExecution*'`

Expected: `BUILD SUCCESSFUL`.

### Task 4: 완료 케이스 입력 잠금

**Files:**
- Modify: `src/main/kotlin/com/safelense/analysis/AnalysisDocumentService.kt`
- Modify: `src/main/kotlin/com/safelense/analysis/AnalysisChecklistService.kt`
- Modify: `src/test/kotlin/com/safelense/analysis/AnalysisDocumentServiceTests.kt`
- Modify: `src/test/kotlin/com/safelense/analysis/AnalysisChecklistServiceTests.kt`

**Interfaces:**
- Consumes: `AnalysisResultRepository.existsByCaseId`.
- Produces: 분석 완료 뒤 문서·체크리스트 변경 시 `AnalysisCaseLockedException`.

- [x] **Step 1: 문서 업로드·삭제와 체크리스트 교체의 잠금 실패 테스트를 추가한다.**

```kotlin
`when`(resultRepository.existsByCaseId(11L)).thenReturn(true)
assertThatThrownBy { mutate() }.isInstanceOf(AnalysisCaseLockedException::class.java)
```

- [x] **Step 2: 잠금 테스트를 실행해 현재 변경이 허용되어 실패하는지 확인한다.**

Run: `./gradlew test --tests 'com.safelense.analysis.AnalysisDocumentServiceTests' --tests 'com.safelense.analysis.AnalysisChecklistServiceTests'`

Expected: 잠금 예외가 발생하지 않아 `FAILED`.

- [x] **Step 3: 케이스 소유 잠금 직후 결과 존재 여부를 확인하는 최소 코드를 추가한다.**

```kotlin
if (resultRepository.existsByCaseId(caseId)) throw AnalysisCaseLockedException()
```

- [x] **Step 4: 문서·체크리스트 집중 테스트가 통과하는지 확인한다.**

Run: `./gradlew test --tests 'com.safelense.analysis.AnalysisDocumentServiceTests' --tests 'com.safelense.analysis.AnalysisChecklistServiceTests'`

Expected: `BUILD SUCCESSFUL`.

### Task 5: 최종 검증과 단일 커밋

**Files:**
- Modify: `docs/work-notes/checklist.md`
- Modify: `docs/work-notes/context-notes.md`

**Interfaces:**
- Consumes: Tasks 1~4의 전체 변경.
- Produces: 검증 증거와 커밋 하나.

- [x] **Step 1: 분석 집중 테스트를 실행한다.**

Run: `./gradlew test --tests 'com.safelense.analysis.*' --rerun-tasks`

Expected: `BUILD SUCCESSFUL`.

- [x] **Step 2: 전체 테스트와 실행 JAR 생성을 실행한다.**

Run: `./gradlew test bootJar --rerun-tasks`

Expected: `BUILD SUCCESSFUL`.

- [x] **Step 3: 공백 오류와 변경 범위를 확인한다.**

Run: `git diff --check`

Expected: 출력 없음.

Run: `git status --short`

Expected: 이 기능의 문서·소스·테스트·마이그레이션만 표시.

- [x] **Step 4: 체크리스트와 컨텍스트 노트에 실제 검증 결과를 기록한다.**

- [x] **Step 5: 모든 변경을 한 번만 스테이징하고 커밋한다.**

```bash
git add docs src
git commit -m "feat: 연습 데이터 기반 위험 분석 실행 추가"
```
