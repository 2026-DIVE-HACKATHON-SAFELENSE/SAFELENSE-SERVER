# Analysis History, Report, and User APIs Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 분석 실행을 제외하고 저장된 분석 결과의 목록·상세·PDF와 내 정보 조회·온보딩 상태 변경 API 5개를 구현한다.

**Architecture:** `analysis_results`를 향후 분석 실행과 현재 조회 API가 공유하는 불변 결과 저장소로 둔다. 조회 서비스는 사용자 ID를 모든 쿼리에 포함하고, PDF 서비스는 상세 결과를 요청 시 문서로 변환한다. 사용자 API는 기존 `UserRepository`와 `users.onboarding_completed`를 그대로 사용한다.

**Tech Stack:** Kotlin 2.3.10, JVM 24, Spring Boot 4.1.0, Spring MVC, Spring Security, Spring Data JPA, Flyway, MySQL, OpenPDF 3.0.5, Nanum Gothic Coding WebJar 4.0.0, JUnit 5, Mockito.

## Global Constraints

- `POST /api/v1/analysis-cases/{caseId}/analyze`는 구현하지 않는다.
- 분석 결과 생성 경로와 규칙 엔진은 구현하지 않는다.
- 분석 목록은 ID 내림차순 커서와 선택적 계약 단계 필터를 지원한다.
- 목록 크기는 기본 20개, 최소 1개, 최대 100개다.
- 모든 결과 조회와 PDF 다운로드는 인증 사용자 ID로 격리한다.
- PDF BLOB을 저장하지 않고 요청 시 저장 결과에서 생성한다.
- 사용자 API는 기존 `users` 테이블만 사용한다.
- PDF 생성 의존성은 OpenPDF와 임베드할 한글 폰트 WebJar로 한정한다.
- 새 Kotlin·SQL 소스 파일은 첫 줄에 역할을 설명하는 한국어 주석을 둔다.
- 무관한 기존 코드는 수정하지 않는다.

---

### Task 1: 분석 결과 영속 모델

**Files:**
- Create: `src/main/resources/db/migration/V6__create_analysis_results.sql`
- Create: `src/main/kotlin/com/safelense/analysis/AnalysisResult.kt`
- Create: `src/main/kotlin/com/safelense/analysis/AnalysisResultRepository.kt`
- Test: `src/test/kotlin/com/safelense/analysis/AnalysisResultMigrationTests.kt`
- Test: `src/test/kotlin/com/safelense/analysis/AnalysisResultRepositoryContractTests.kt`

**Interfaces:**
- Consumes: `AnalysisStage`.
- Produces: `AnalysisRiskGrade`, `AnalysisResult`, `AnalysisResultRepository.findByUserIdWithCursor(...)`, `findByIdAndUserId(...)`.

- [ ] **Step 1: 마이그레이션과 저장소 계약 실패 테스트 작성**

```kotlin
// 분석 결과 테이블의 소유권과 조회 제약을 검증하는 테스트
class AnalysisResultMigrationTests {
    @Test
    fun `analysis result migration defines ownership and history indexes`() {
        val sql = ClassPathResource("db/migration/V6__create_analysis_results.sql")
            .inputStream.bufferedReader().use { it.readText() }

        assertThat(sql).contains("CREATE TABLE analysis_results")
        assertThat(sql).contains("UNIQUE (case_id)")
        assertThat(sql).contains("INDEX idx_analysis_results_user_id_id (user_id, id)")
        assertThat(sql).contains("FOREIGN KEY (case_id) REFERENCES analysis_cases(id)")
        assertThat(sql).contains("FOREIGN KEY (user_id) REFERENCES users(id)")
    }
}
```

저장소 계약 테스트는 사용자 ID, 커서, 선택적 단계와 `Pageable`을 받는 쿼리 선언 및 `findByIdAndUserId` 존재를 리플렉션으로 검증한다.

- [ ] **Step 2: 실패 확인**

Run:

```bash
./gradlew test --tests 'com.safelense.analysis.AnalysisResult*'
```

Expected: V6 파일과 분석 결과 타입이 없어 FAIL.

- [ ] **Step 3: V6와 JPA 모델 최소 구현**

`analysis_results`에는 `case_id`, `user_id`, `property_id`, `stage`, nullable `score`, `grade`, `confidence`, `summary`, `findings`, `recommendations`, `rule_version`, `analyzed_at`을 둔다.

```kotlin
enum class AnalysisRiskGrade {
    UNKNOWN,
    LOW,
    MEDIUM,
    HIGH,
}

@Entity
@Table(name = "analysis_results")
class AnalysisResult(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    val caseId: Long,
    val userId: Long,
    val propertyId: Long,
    val stage: AnalysisStage,
    val score: Int?,
    val grade: AnalysisRiskGrade,
    val confidence: Int,
    val summary: String,
    val findings: String,
    val recommendations: String,
    val ruleVersion: String,
    val analyzedAt: Instant,
)
```

저장소 쿼리는 `userId`, optional `cursor`, optional `stage`를 조건으로 사용하고 `id desc`로 정렬한다.

- [ ] **Step 4: 모델 집중 테스트 통과 확인**

Run:

```bash
./gradlew test --tests 'com.safelense.analysis.AnalysisResultMigrationTests' --tests 'com.safelense.analysis.AnalysisResultRepositoryContractTests'
```

Expected: PASS.

- [ ] **Step 5: 영속 모델 커밋**

```bash
git add src/main/resources/db/migration/V6__create_analysis_results.sql src/main/kotlin/com/safelense/analysis/AnalysisResult.kt src/main/kotlin/com/safelense/analysis/AnalysisResultRepository.kt src/test/kotlin/com/safelense/analysis/AnalysisResultMigrationTests.kt src/test/kotlin/com/safelense/analysis/AnalysisResultRepositoryContractTests.kt
git commit -m "feat: 분석 결과 저장 모델 추가"
```

### Task 2: 분석 이력 목록과 상세 조회

**Files:**
- Create: `src/main/kotlin/com/safelense/analysis/AnalysisResultService.kt`
- Create: `src/main/kotlin/com/safelense/analysis/AnalysisResultController.kt`
- Modify: `src/main/kotlin/com/safelense/analysis/AnalysisExceptions.kt`
- Modify: `src/main/kotlin/com/safelense/auth/presentation/ApiExceptionHandler.kt`
- Test: `src/test/kotlin/com/safelense/analysis/AnalysisResultServiceTests.kt`
- Test: `src/test/kotlin/com/safelense/analysis/AnalysisResultControllerTests.kt`

**Interfaces:**
- Consumes: `AnalysisResultRepository`.
- Produces: `AnalysisHistoryPage`, `AnalysisResultDetail`, `GET /api/v1/analyses`, `GET /api/v1/analyses/{analysisId}`.

- [ ] **Step 1: 서비스 실패 테스트 작성**

```kotlin
@Test
fun `lists owned analysis results with a next cursor`() {
    whenever(repository.findByUserIdWithCursor(eq(7L), isNull(), isNull(), any()))
        .thenReturn(listOf(result(31L), result(30L), result(29L)))

    val page = service.list(7L, null, 2, null)

    assertThat(page.items.map { it.id }).containsExactly(31L, 30L)
    assertThat(page.nextCursor).isEqualTo(30L)
    assertThat(page.hasNext).isTrue()
}

@Test
fun `hides a result not owned by the user`() {
    whenever(repository.findByIdAndUserId(31L, 7L)).thenReturn(null)

    assertThatThrownBy { service.get(7L, 31L) }
        .isInstanceOf(AnalysisResultNotFoundException::class.java)
}
```

잘못된 커서, 크기, 단계와 줄바꿈 문자열의 목록 변환도 각각 검증한다.

- [ ] **Step 2: 서비스 테스트 실패 확인**

Run:

```bash
./gradlew test --tests 'com.safelense.analysis.AnalysisResultServiceTests'
```

Expected: 서비스와 응답 타입이 없어 FAIL.

- [ ] **Step 3: 목록·상세 서비스 최소 구현**

```kotlin
@Transactional(readOnly = true)
fun list(userId: Long, cursor: Long?, size: Int, stage: AnalysisStage?): AnalysisHistoryPage {
    if (cursor != null && cursor <= 0 || size !in 1..100) {
        throw InvalidAnalysisResultRequestException()
    }
    val rows = repository.findByUserIdWithCursor(userId, cursor, stage, PageRequest.of(0, size + 1))
    val hasNext = rows.size > size
    val items = rows.take(size).map { it.toSummary() }
    return AnalysisHistoryPage(items, if (hasNext) items.last().id else null, hasNext)
}
```

상세 조회는 `findByIdAndUserId`만 사용하고 `findings`, `recommendations`를 빈 줄을 제외한 문자열 목록으로 변환한다.

- [ ] **Step 4: 서비스 테스트 통과 확인**

Run:

```bash
./gradlew test --tests 'com.safelense.analysis.AnalysisResultServiceTests'
```

Expected: PASS.

- [ ] **Step 5: MVC 실패 테스트 작성**

목록의 기본 크기, 커서·단계 전달, 잘못된 파라미터 400, 상세 200, 미소유 상세 404를 검증한다.

```kotlin
mockMvc.perform(get("/api/v1/analyses").principal(authentication))
    .andExpect(status().isOk)
    .andExpect(jsonPath("$.analyses[0].id").value(31))
    .andExpect(jsonPath("$.nextCursor").value(30))

mockMvc.perform(get("/api/v1/analyses/31").principal(authentication))
    .andExpect(status().isOk)
    .andExpect(jsonPath("$.findings[0]").value("위험 근거"))
```

- [ ] **Step 6: 컨트롤러와 오류 매핑 구현**

문자열 파라미터는 컨트롤러에서 안전하게 숫자와 `AnalysisStage`로 변환하고 실패 시 `InvalidAnalysisResultRequestException`을 던진다.

오류 응답은 다음 두 계약을 추가한다.

```kotlin
400 INVALID_REQUEST
404 ANALYSIS_NOT_FOUND
```

- [ ] **Step 7: 분석 조회 집중 테스트 통과 확인**

Run:

```bash
./gradlew test --tests 'com.safelense.analysis.AnalysisResult*'
```

Expected: PASS.

- [ ] **Step 8: 분석 조회 커밋**

```bash
git add src/main/kotlin/com/safelense/analysis src/main/kotlin/com/safelense/auth/presentation/ApiExceptionHandler.kt src/test/kotlin/com/safelense/analysis/AnalysisResultServiceTests.kt src/test/kotlin/com/safelense/analysis/AnalysisResultControllerTests.kt
git commit -m "feat: 분석 이력과 결과 조회 API 추가"
```

### Task 3: PDF 리포트 다운로드

**Files:**
- Modify: `build.gradle.kts`
- Create: `src/main/kotlin/com/safelense/analysis/AnalysisReportService.kt`
- Modify: `src/main/kotlin/com/safelense/analysis/AnalysisResultController.kt`
- Test: `src/test/kotlin/com/safelense/analysis/AnalysisReportServiceTests.kt`
- Modify: `src/test/kotlin/com/safelense/analysis/AnalysisResultControllerTests.kt`

**Interfaces:**
- Consumes: `AnalysisResultService.get(userId, analysisId)`.
- Produces: `AnalysisReportService.create(detail): ByteArray`, `GET /api/v1/analyses/{analysisId}/report.pdf`.

- [ ] **Step 1: PDF 실패 테스트 작성**

```kotlin
@Test
fun `creates a pdf report from stored analysis detail`() {
    val bytes = service.create(detail())

    assertThat(bytes.copyOfRange(0, 5).toString(Charsets.US_ASCII)).isEqualTo("%PDF-")
    assertThat(bytes.size).isGreaterThan(500)
}
```

MVC 테스트는 `application/pdf`, `%PDF-` 본문과 `attachment; filename="safelense-analysis-31.pdf"`를 검증한다.

- [ ] **Step 2: PDF 테스트 실패 확인**

Run:

```bash
./gradlew test --tests 'com.safelense.analysis.AnalysisReportServiceTests' --tests 'com.safelense.analysis.AnalysisResultControllerTests'
```

Expected: PDF 서비스와 경로가 없어 FAIL.

- [ ] **Step 3: OpenPDF와 최소 문서 구현**

`build.gradle.kts`에 PDF 생성 라이브러리와 실행 환경에 관계없이 임베드할 한글 폰트를 추가한다.

```kotlin
implementation("com.github.librepdf:openpdf:3.0.5")
implementation("org.webjars.npm:nanum-gothic-coding:4.0.0")
```

PDF에는 제목, 분석 메타데이터, 요약, 발견 사항과 권고사항을 순서대로 추가한다. `NanumGothicCoding-Regular.ttf`를 PDF에 임베드하고 별도 템플릿 엔진은 만들지 않는다.

- [ ] **Step 4: PDF 집중 테스트 통과 확인**

Run:

```bash
./gradlew test --tests 'com.safelense.analysis.AnalysisReportServiceTests' --tests 'com.safelense.analysis.AnalysisResultControllerTests'
```

Expected: PASS.

- [ ] **Step 5: PDF 커밋**

```bash
git add build.gradle.kts src/main/kotlin/com/safelense/analysis/AnalysisReportService.kt src/main/kotlin/com/safelense/analysis/AnalysisResultController.kt src/test/kotlin/com/safelense/analysis/AnalysisReportServiceTests.kt src/test/kotlin/com/safelense/analysis/AnalysisResultControllerTests.kt
git commit -m "feat: 분석 PDF 리포트 다운로드 추가"
```

### Task 4: 내 정보와 온보딩 상태

**Files:**
- Create: `src/main/kotlin/com/safelense/user/UserService.kt`
- Create: `src/main/kotlin/com/safelense/user/UserController.kt`
- Modify: `src/main/kotlin/com/safelense/auth/presentation/ApiExceptionHandler.kt`
- Test: `src/test/kotlin/com/safelense/user/UserServiceTests.kt`
- Test: `src/test/kotlin/com/safelense/user/UserControllerTests.kt`

**Interfaces:**
- Consumes: `UserRepository.findById(userId)`.
- Produces: `UserView`, `GET /api/v1/me`, `PATCH /api/v1/me/onboarding`.

- [ ] **Step 1: 사용자 서비스 실패 테스트 작성**

```kotlin
@Test
fun `gets the authenticated user profile`() {
    whenever(repository.findById(7L)).thenReturn(Optional.of(user()))

    val result = service.get(7L)

    assertThat(result.id).isEqualTo(7L)
    assertThat(result.nickname).isEqualTo("세입자")
    assertThat(result.onboardingCompleted).isFalse()
}

@Test
fun `updates onboarding state`() {
    val user = user()
    whenever(repository.findById(7L)).thenReturn(Optional.of(user))

    val result = service.updateOnboarding(7L, true)

    assertThat(user.onboardingCompleted).isTrue()
    assertThat(result.onboardingCompleted).isTrue()
}
```

사용자 없음 예외도 두 경로에서 검증한다.

- [ ] **Step 2: 사용자 서비스 테스트 실패 확인**

Run:

```bash
./gradlew test --tests 'com.safelense.user.UserServiceTests'
```

Expected: 사용자 서비스가 없어 FAIL.

- [ ] **Step 3: 사용자 서비스 최소 구현**

`UserNotFoundException`, `UserView`, 읽기 전용 조회와 트랜잭션 상태 변경을 한 파일에 구현한다. 상태 변경은 JPA 변경 감지를 사용하고 불필요한 `save`를 호출하지 않는다.

- [ ] **Step 4: 사용자 서비스 테스트 통과 확인**

Run:

```bash
./gradlew test --tests 'com.safelense.user.UserServiceTests'
```

Expected: PASS.

- [ ] **Step 5: 사용자 MVC 실패 테스트 작성**

```kotlin
mockMvc.perform(get("/api/v1/me").principal(authentication))
    .andExpect(status().isOk)
    .andExpect(jsonPath("$.nickname").value("세입자"))

mockMvc.perform(
    patch("/api/v1/me/onboarding")
        .principal(authentication)
        .contentType(MediaType.APPLICATION_JSON)
        .content("""{"onboardingCompleted":true}"""),
)
    .andExpect(status().isOk)
    .andExpect(jsonPath("$.onboardingCompleted").value(true))
```

빈 본문과 잘못된 타입은 400, 사용자 없음은 404를 검증한다.

- [ ] **Step 6: 사용자 컨트롤러와 오류 매핑 구현**

요청 필드는 nullable `Boolean?`과 `@NotNull`로 선언해 누락을 Bean Validation으로 거절하고, 잘못된 타입은 JSON 파싱 오류로 거절한다. `UserNotFoundException`은 `404 USER_NOT_FOUND`로 변환한다.

- [ ] **Step 7: 사용자 집중 테스트 통과 확인**

Run:

```bash
./gradlew test --tests 'com.safelense.user.*'
```

Expected: PASS.

- [ ] **Step 8: 사용자 API 커밋**

```bash
git add src/main/kotlin/com/safelense/user src/main/kotlin/com/safelense/auth/presentation/ApiExceptionHandler.kt src/test/kotlin/com/safelense/user
git commit -m "feat: 내 정보와 온보딩 API 추가"
```

### Task 5: 전체 검증과 문서 마감

**Files:**
- Modify: `docs/work-notes/checklist.md`
- Modify: `docs/work-notes/context-notes.md`

**Interfaces:**
- Produces: 검증 근거와 남은 운영 위험 기록.

- [ ] **Step 1: 분석과 사용자 집중 테스트**

```bash
./gradlew test --tests 'com.safelense.analysis.*' --tests 'com.safelense.user.*' --rerun-tasks
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: 전체 테스트와 실행 JAR**

```bash
./gradlew test --rerun-tasks
./gradlew bootJar
```

Expected: 두 명령 모두 `BUILD SUCCESSFUL`.

- [ ] **Step 3: 정적 검토**

```bash
git diff --check
git status --short
git log --oneline main..HEAD
```

Expected: 공백 오류 없음, 의도한 파일만 변경, 의미 단위 커밋 확인.

- [ ] **Step 4: 체크리스트와 컨텍스트 노트 갱신**

실제로 실행한 명령, 결과, 실제 MySQL 통합 검증 여부와 분석 결과 생성 경로가 없다는 점을 기록한다.

- [ ] **Step 5: 문서 마감 커밋**

```bash
git add docs/work-notes/checklist.md docs/work-notes/context-notes.md docs/superpowers/plans/2026-07-24-analysis-history-report-user.md
git commit -m "docs: 분석 리포트 API 검증 결과 기록"
```

- [ ] **Step 6: main 병합 후 재검증과 푸시**

```bash
git switch main
git pull --ff-only origin main
git merge --ff-only feat/report
./gradlew test --rerun-tasks
./gradlew bootJar
git push origin main
```

Expected: 병합 후 검증 성공과 `origin/main` 푸시 성공.
