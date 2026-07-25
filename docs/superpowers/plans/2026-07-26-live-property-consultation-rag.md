# Live Property Data and Consultation RAG Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 신규 계약 분석에서 DEMO 시드와 고정 상담 사례를 제거하고, 실제 공공데이터 조회와 임차in 상담 938건의 하이브리드 검색을 사용해 `LIVE` 리포트를 생성한다.

**Architecture:** 자유 형식 주소를 먼저 정규화한 뒤 건축물대장·공동주택가격·주택유형별 전월세 API를 독립 호출하고, 각 결과를 실행별 근거로 저장한다. 규칙 엔진은 공동주택가격만 추정 주택가액으로 사용하며 전월세 거래는 `RENT_MARKET` 문맥 근거로만 사용한다. 상담 XLSX는 일회성 명령으로 PostgreSQL에 적재하고 OpenAI 임베딩과 구조화 점수를 45:55로 결합해 상위 3건을 실행별 스냅샷으로 저장한다.

**Tech Stack:** Kotlin 2.3.10, Java 24, Spring Boot 4.1.0, Spring Web `RestClient`, Spring Data JPA, PostgreSQL, Flyway, Jackson 3, Apache POI OOXML, OpenAI Embeddings API, JUnit 5, Mockito, AssertJ.

## Global Constraints

- 신규 분석 실행은 항상 `AnalysisDataMode.LIVE`로 생성하고 기존 `DEMO` 값은 과거 행 역직렬화 호환성만 유지한다.
- API 키 값은 소스·테스트 fixture·문서·로그에 기록하지 않고 `PUBLIC_DATA_SERVICE_KEY`, `VWORLD_API_KEY`, `OPENAI_API_KEY`로만 주입한다.
- 공동주택가격은 `OFFICIAL_PRICE`, 전월세 보증금 통계는 `RENT_MARKET`으로 저장하며 전월세 보증금을 `TRANSACTION_PRICE`나 주택가액으로 변환하지 않는다.
- 건축물대장·공동주택가격·전월세 조회 실패는 서로 격리하고 실패한 제공처만 `UNAVAILABLE`로 저장한다.
- 정상 조회 결과가 0건이거나 지원하지 않는 데이터는 `NOT_AVAILABLE`, 주소 해석·인증·통신·응답 파싱 실패는 `UNAVAILABLE`로 저장한다.
- 연립다세대 전월세, 도시계획, 재개발·재건축, 침수이력, HUG 보증 가입 최종 판정은 이번 범위에서 `NOT_AVAILABLE`로 저장한다.
- 등기부는 업로드 및 추출 상태만 `REGISTRY_DOCUMENT` 근거로 변환하고 OCR·권리관계 판정은 하지 않는다.
- 원본 상담 XLSX는 저장소에 복사하거나 커밋하지 않고 외부 절대 경로에서 일회성으로 읽는다.
- 상담 데이터 출처는 `DIVE_2026_COUNSELING`, 데이터셋 버전은 `2026-v1`로 저장하며 HUG 사례로 표기하지 않는다.
- 상담 검색 구조화 점수는 보증금구간 30, 주택유형 20, 선순위권리 25, 보증보험 15, 시도 10을 사용하고 알 수 없는 입력은 분모에서 제외한다.
- 결합 점수는 구조화 55%, 의미 유사도 45%이며 구조화 상위 100건 중 결합 점수 0.45 이상 상위 3건만 반환한다.
- 새 Kotlin 소스 파일 첫 줄에는 해당 파일 역할을 설명하는 한 줄 한국어 주석을 둔다.
- 기존 사용자의 관련 없는 수정은 변경하거나 정리하지 않는다.

---

### Task 1: 상담 사례 스키마와 운영 설정

**Files:**
- Create: `src/main/resources/db/migration/V10__create_consultation_case_search.sql`
- Create: `src/main/kotlin/com/safelense/analysis/match/ConsultationCase.kt`
- Create: `src/main/kotlin/com/safelense/analysis/match/AnalysisCaseMatch.kt`
- Create: `src/main/kotlin/com/safelense/analysis/match/ConsultationCaseRepository.kt`
- Create: `src/main/kotlin/com/safelense/analysis/match/AnalysisCaseMatchRepository.kt`
- Create: `src/main/kotlin/com/safelense/analysis/collection/PublicDataProperties.kt`
- Create: `src/main/kotlin/com/safelense/analysis/collection/VWorldProperties.kt`
- Modify: `src/main/kotlin/com/safelense/analysis/interpretation/OpenAiProperties.kt`
- Modify: `src/main/kotlin/com/safelense/config/SsmEnvironmentPostProcessor.kt`
- Modify: `src/main/resources/application.yml`
- Modify: `build.gradle.kts`
- Test: `src/test/kotlin/com/safelense/analysis/ConsultationCaseMigrationTests.kt`
- Test: `src/test/kotlin/com/safelense/analysis/collection/PublicDataConfigurationTests.kt`
- Modify: `src/test/kotlin/com/safelense/config/SsmEnvironmentPostProcessorTests.kt`

**Interfaces:**
- Produces: `ConsultationCase`, `AnalysisCaseMatch`, `ConsultationCaseRepository.findAll()`, `AnalysisCaseMatchRepository.findAllByRunIdOrderByRankAsc(runId: Long)`.
- Produces: `PublicDataProperties`, `VWorldProperties`, `OpenAiProperties.embeddingModel`.
- Consumes: 기존 `AnalysisRun`, Spring Boot configuration properties scan, PostgreSQL Flyway.

- [ ] **Step 1: 실패하는 마이그레이션·설정 테스트를 작성한다.**

```kotlin
// 상담 사례 검색 스키마가 필수 테이블과 제약을 만드는지 검증하는 테스트
class ConsultationCaseMigrationTests {
    @Test
    fun `migration creates consultation cases and run matches`() {
        val sql = ClassPathResource(
            "db/migration/V10__create_consultation_case_search.sql",
        ).inputStream.bufferedReader().use { it.readText() }

        assertThat(sql).contains(
            "CREATE TABLE consultation_cases",
            "external_case_id VARCHAR(64) NOT NULL",
            "embedding_json TEXT NULL",
            "CREATE TABLE analysis_case_matches",
            "UNIQUE (run_id, rank)",
            "UNIQUE (run_id, consultation_case_id)",
        )
    }
}
```

```kotlin
// 실제 공공데이터와 임베딩 설정의 환경변수 바인딩을 검증하는 테스트
class PublicDataConfigurationTests {
    private val contextRunner = ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration::class.java))
        .withUserConfiguration(TestConfiguration::class.java)
        .withPropertyValues(
            "app.public-data.service-key=public-key",
            "app.vworld.api-key=vworld-key",
            "app.openai.api-key=openai-key",
            "app.openai.embedding-model=text-embedding-3-small",
        )

    @Test
    fun `binds provider keys and embedding model`() {
        contextRunner.run {
            assertThat(it.getBean(PublicDataProperties::class.java).serviceKey).isEqualTo("public-key")
            assertThat(it.getBean(VWorldProperties::class.java).apiKey).isEqualTo("vworld-key")
            assertThat(it.getBean(OpenAiProperties::class.java).embeddingModel)
                .isEqualTo("text-embedding-3-small")
        }
    }
}
```

- [ ] **Step 2: 새 테스트가 파일과 프로퍼티 부재로 실패하는지 확인한다.**

Run: `./gradlew test --tests 'com.safelense.analysis.ConsultationCaseMigrationTests' --tests 'com.safelense.analysis.collection.PublicDataConfigurationTests' --tests 'com.safelense.config.SsmEnvironmentPostProcessorTests' --rerun-tasks`

Expected: `V10__create_consultation_case_search.sql`, `PublicDataProperties`, `VWorldProperties`, `embeddingModel` 부재로 컴파일 또는 테스트 실패.

- [ ] **Step 3: V10 스키마와 JPA 모델을 최소 구현한다.**

```sql
-- 실제 임차인 상담 사례와 분석 실행별 검색 스냅샷을 저장하는 테이블
CREATE TABLE consultation_cases (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    external_case_id VARCHAR(64) NOT NULL,
    source VARCHAR(64) NOT NULL,
    dataset_version VARCHAR(32) NOT NULL,
    source_group VARCHAR(100) NOT NULL,
    consultation_month VARCHAR(7) NOT NULL,
    province VARCHAR(50) NOT NULL,
    district VARCHAR(50) NOT NULL,
    deposit_band VARCHAR(50) NOT NULL,
    contract_status VARCHAR(50) NOT NULL,
    housing_type VARCHAR(50) NOT NULL,
    senior_rights VARCHAR(100) NOT NULL,
    guarantee_status VARCHAR(100) NOT NULL,
    dispute_type VARCHAR(100) NOT NULL,
    progress_stage VARCHAR(100) NOT NULL,
    situation_summary TEXT NULL,
    counselor_opinion TEXT NULL,
    special_notes TEXT NULL,
    embedding_json TEXT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_consultation_cases_source_external UNIQUE (source, external_case_id)
);

CREATE INDEX idx_consultation_cases_structured
    ON consultation_cases (deposit_band, housing_type, province);

CREATE TABLE analysis_case_matches (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    run_id BIGINT NOT NULL,
    consultation_case_id BIGINT NOT NULL,
    rank INT NOT NULL,
    structured_score DOUBLE PRECISION NOT NULL,
    semantic_score DOUBLE PRECISION NULL,
    combined_score DOUBLE PRECISION NOT NULL,
    pattern VARCHAR(255) NOT NULL,
    summary TEXT NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_analysis_case_matches_run_rank UNIQUE (run_id, rank),
    CONSTRAINT uk_analysis_case_matches_run_case UNIQUE (run_id, consultation_case_id),
    CONSTRAINT fk_analysis_case_matches_run_id
        FOREIGN KEY (run_id) REFERENCES analysis_runs(id) ON DELETE CASCADE,
    CONSTRAINT fk_analysis_case_matches_case_id
        FOREIGN KEY (consultation_case_id) REFERENCES consultation_cases(id) ON DELETE RESTRICT
);

CREATE INDEX idx_analysis_case_matches_run_id ON analysis_case_matches (run_id, rank);
```

`ConsultationCase`는 V10 컬럼을 그대로 매핑하고 `externalCaseId`, 구조화 필드, 세 개의 상담 텍스트, `embeddingJson`을 변경 가능한 프로퍼티로 둔다. `AnalysisCaseMatch`는 `runId`, `consultationCaseId`, `rank`, 세 점수, `pattern`, `summary`를 불변 프로퍼티로 둔다.

- [ ] **Step 4: 실제 키와 기본 URL을 환경변수로 바인딩한다.**

```kotlin
// 공공데이터포털 건축물대장과 전월세 API 설정을 바인딩하는 속성
@ConfigurationProperties("app.public-data")
data class PublicDataProperties(
    val serviceKey: String,
    val buildingBaseUrl: String = "https://apis.data.go.kr/1613000/BldRgstHubService",
    val apartmentRentBaseUrl: String = "https://apis.data.go.kr/1613000/RTMSDataSvcAptRent",
    val officetelRentBaseUrl: String = "https://apis.data.go.kr/1613000/RTMSDataSvcOffiRent",
    val detachedRentBaseUrl: String = "https://apis.data.go.kr/1613000/RTMSDataSvcSHRent",
)
```

```kotlin
// VWorld 주소와 공동주택가격 API 설정을 바인딩하는 속성
@ConfigurationProperties("app.vworld")
data class VWorldProperties(
    val apiKey: String,
    val searchBaseUrl: String = "https://api.vworld.kr/req/search",
    val officialPriceBaseUrl: String =
        "https://api.vworld.kr/ned/data/getApartHousingPriceAttr",
)
```

`OpenAiProperties`에는 `val embeddingModel: String = "text-embedding-3-small"`을 추가한다. `application.yml`에는 위 URL과 `${PUBLIC_DATA_SERVICE_KEY:}`, `${VWORLD_API_KEY:}`, `${OPENAI_EMBEDDING_MODEL:text-embedding-3-small}`만 추가한다. SSM 목록에는 `/safelense/prod/PUBLIC_DATA_SERVICE_KEY`, `/safelense/prod/VWORLD_API_KEY`를 추가하고 기존 10개 단위 분할 조회 테스트를 13개 이름에 맞춘다.

- [ ] **Step 5: Apache POI 의존성을 추가하고 집중 테스트를 통과시킨다.**

```kotlin
implementation("org.apache.poi:poi-ooxml:5.4.1")
```

Run: `./gradlew test --tests 'com.safelense.analysis.ConsultationCaseMigrationTests' --tests 'com.safelense.analysis.collection.PublicDataConfigurationTests' --tests 'com.safelense.config.SsmEnvironmentPostProcessorTests' --rerun-tasks`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: 첫 의미 단위를 커밋한다.**

```bash
git add build.gradle.kts src/main/resources/application.yml src/main/resources/db/migration/V10__create_consultation_case_search.sql src/main/kotlin/com/safelense/analysis/match src/main/kotlin/com/safelense/analysis/collection/PublicDataProperties.kt src/main/kotlin/com/safelense/analysis/collection/VWorldProperties.kt src/main/kotlin/com/safelense/analysis/interpretation/OpenAiProperties.kt src/main/kotlin/com/safelense/config/SsmEnvironmentPostProcessor.kt src/test/kotlin/com/safelense/analysis/ConsultationCaseMigrationTests.kt src/test/kotlin/com/safelense/analysis/collection/PublicDataConfigurationTests.kt src/test/kotlin/com/safelense/config/SsmEnvironmentPostProcessorTests.kt
git commit -m "feat: 상담 검색 스키마와 실조회 설정 추가"
```

---

### Task 2: 주소 해석과 공공데이터 HTTP 어댑터

**Files:**
- Create: `src/main/kotlin/com/safelense/analysis/collection/ResolvedPropertyAddress.kt`
- Create: `src/main/kotlin/com/safelense/analysis/collection/PropertyAddressResolver.kt`
- Create: `src/main/kotlin/com/safelense/analysis/collection/VWorldAddressResolver.kt`
- Create: `src/main/kotlin/com/safelense/analysis/collection/BuildingRegisterClient.kt`
- Create: `src/main/kotlin/com/safelense/analysis/collection/VWorldOfficialPriceClient.kt`
- Create: `src/main/kotlin/com/safelense/analysis/collection/MolitRentMarketClient.kt`
- Create: `src/main/kotlin/com/safelense/analysis/collection/SafeXml.kt`
- Test: `src/test/kotlin/com/safelense/analysis/collection/VWorldAddressResolverTests.kt`
- Test: `src/test/kotlin/com/safelense/analysis/collection/BuildingRegisterClientTests.kt`
- Test: `src/test/kotlin/com/safelense/analysis/collection/VWorldOfficialPriceClientTests.kt`
- Test: `src/test/kotlin/com/safelense/analysis/collection/MolitRentMarketClientTests.kt`

**Interfaces:**
- Produces: `PropertyAddressResolver.resolve(address: String): ResolvedPropertyAddress?`.
- Produces: `BuildingRegisterClient.fetch(address: ResolvedPropertyAddress): BuildingRegisterSnapshot?`.
- Produces: `OfficialPriceClient.fetch(address: ResolvedPropertyAddress, year: Int): OfficialPriceSnapshot?`.
- Produces: `RentMarketClient.fetch(address: ResolvedPropertyAddress, buildingType: BuildingType, months: List<YearMonth>): RentMarketSnapshot?`.
- Consumes: `RestClient.Builder`, provider properties, Jackson `ObjectMapper`.

- [ ] **Step 1: 주소 정규화 실패 테스트와 성공 fixture 테스트를 먼저 작성한다.**

```kotlin
// VWorld 응답에서 공공데이터 조회에 필요한 법정동·지번 코드를 추출하는 테스트
class VWorldAddressResolverTests {
    @Test
    fun `resolves one road address to pnu and building query codes`() {
        server.enqueue(MockResponse().setBody(vworldAddressFixture))
        val result = resolver().resolve("서울특별시 중구 세종대로 110")

        assertThat(result).isEqualTo(
            ResolvedPropertyAddress(
                pnu = "1114010300100310000",
                sigunguCode = "11140",
                bjdongCode = "10300",
                platGbCode = "0",
                bun = "0031",
                ji = "0000",
                province = "서울특별시",
                district = "중구",
                legalDong = "태평로1가",
                longitude = 126.977829,
                latitude = 37.566317,
            ),
        )
    }

    @Test
    fun `returns null for ambiguous or zero address results`() {
        server.enqueue(MockResponse().setBody("""{"response":{"status":"OK","record":{"total":"2"}}}"""))
        assertThat(resolver().resolve("세종대로")).isNull()
    }
}
```

테스트 HTTP 서버는 JDK `HttpServer`를 사용해 실제 요청 query를 캡처하고 키 값이 로그나 예외 문자열에 들어가지 않는 것도 함께 확인한다.

- [ ] **Step 2: 테스트가 주소 해석 타입 부재로 실패하는지 확인한다.**

Run: `./gradlew test --tests 'com.safelense.analysis.collection.VWorldAddressResolverTests' --rerun-tasks`

Expected: 새 resolver와 데이터 타입 부재로 컴파일 실패.

- [ ] **Step 3: 주소 검색 어댑터를 최소 구현한다.**

```kotlin
// 공공데이터 조회용으로 정규화된 주소 코드와 좌표를 표현하는 값
data class ResolvedPropertyAddress(
    val pnu: String,
    val sigunguCode: String,
    val bjdongCode: String,
    val platGbCode: String,
    val bun: String,
    val ji: String,
    val province: String,
    val district: String,
    val legalDong: String,
    val longitude: Double,
    val latitude: Double,
)

fun interface PropertyAddressResolver {
    fun resolve(address: String): ResolvedPropertyAddress?
}
```

`VWorldAddressResolver`는 `service=search`, `request=search`, `version=2.0`, `crs=EPSG:4326`, `size=2`, `page=1`, `query`, `type=address`, `category=road`, `format=json`, `errorformat=json`, `key`를 query parameter로 보낸다. `response.status == "OK"`이고 결과가 정확히 한 건일 때만 item의 `id`를 PNU로 사용하며, PNU 길이가 19가 아니면 `null`을 반환한다.

- [ ] **Step 4: 건축물대장·공동주택가격·전월세 파싱 실패 테스트를 작성한다.**

```kotlin
@Test
fun `building register maps title response without exposing the key`() {
    server.enqueue(jsonResponse(buildingFixture))
    val result = client().fetch(address)

    assertThat(result).isEqualTo(
        BuildingRegisterSnapshot(
            mainPurpose = "업무시설",
            approvalDate = LocalDate.parse("2020-01-02"),
            structure = "철근콘크리트구조",
            groundFloors = 20,
            undergroundFloors = 3,
            violationBuilding = false,
        ),
    )
    assertThat(server.takeRequest().path).contains(
        "/getBrTitleInfo",
        "sigunguCd=11140",
        "bjdongCd=10300",
        "_type=json",
    )
}
```

```kotlin
@Test
fun `rent client aggregates six months of deposit without producing sale price`() {
    repeat(6) { server.enqueue(xmlResponse(rentFixture(it + 1))) }
    val result = client().fetch(address, BuildingType.APARTMENT, months)

    assertThat(result?.sampleCount).isEqualTo(6)
    assertThat(result?.medianDepositManwon).isEqualTo(31500)
    assertThat(result?.minimumDepositManwon).isEqualTo(29000)
    assertThat(result?.maximumDepositManwon).isEqualTo(34000)
    assertThat(result?.fromMonth).isEqualTo(YearMonth.parse("2026-02"))
    assertThat(result?.toMonth).isEqualTo(YearMonth.parse("2026-07"))
}
```

공동주택가격 테스트는 `pnu`, 현재 연도, `format=json`, `key` query와 `pblntfPc`의 만원 단위 변환을 검증한다. 금액 문자열의 쉼표 제거와 원 단위에서 만원 단위로의 정수 나눗셈을 fixture에 고정한다.

- [ ] **Step 5: 새 제공처 테스트가 타입과 구현 부재로 실패하는지 확인한다.**

Run: `./gradlew test --tests 'com.safelense.analysis.collection.BuildingRegisterClientTests' --tests 'com.safelense.analysis.collection.VWorldOfficialPriceClientTests' --tests 'com.safelense.analysis.collection.MolitRentMarketClientTests' --rerun-tasks`

Expected: HTTP 클라이언트 타입 부재로 컴파일 실패.

- [ ] **Step 6: 제공처 경계와 안전한 XML 파서를 구현한다.**

```kotlin
// 건축물대장의 리포트 사용 필드만 정규화한 값
data class BuildingRegisterSnapshot(
    val mainPurpose: String?,
    val approvalDate: LocalDate?,
    val structure: String?,
    val groundFloors: Int?,
    val undergroundFloors: Int?,
    val violationBuilding: Boolean?,
)

fun interface BuildingRegisterClient {
    fun fetch(address: ResolvedPropertyAddress): BuildingRegisterSnapshot?
}

data class OfficialPriceSnapshot(
    val amountManwon: Long,
    val standardYear: Int,
    val standardMonth: Int?,
)

fun interface OfficialPriceClient {
    fun fetch(address: ResolvedPropertyAddress, year: Int): OfficialPriceSnapshot?
}

data class RentMarketSnapshot(
    val sampleCount: Int,
    val medianDepositManwon: Long,
    val minimumDepositManwon: Long,
    val maximumDepositManwon: Long,
    val fromMonth: YearMonth,
    val toMonth: YearMonth,
)

fun interface RentMarketClient {
    fun fetch(
        address: ResolvedPropertyAddress,
        buildingType: BuildingType,
        months: List<YearMonth>,
    ): RentMarketSnapshot?
}
```

`BuildingRegisterHttpClient`는 `/getBrTitleInfo`를 JSON으로 호출한다. `VWorldOfficialPriceClient`는 `pnu`, `stdrYear`, `format=json`, `numOfRows=1000`, `pageNo=1`, `key`를 사용한다. 최신 기준연도 결과에서 가격 행을 정확히 한 건 식별할 때만 반환하고, 동·호 등 상세 정보가 없어 여러 행이 남으면 추측 선택하지 않고 `null`을 반환한다. `MolitRentMarketClient`는 `APARTMENT → /getRTMSDataSvcAptRent`, `OFFICETEL → /getRTMSDataSvcOffiRent`, `DETACHED_HOUSE → /getRTMSDataSvcSHRent`를 선택하고 `MULTI_FAMILY`는 호출 없이 `null`을 반환한다.

`SafeXml`은 `DocumentBuilderFactory`의 `disallow-doctype-decl`, external general entities, external parameter entities를 차단한다. 전월세 응답의 `resultCode`가 정상 코드가 아니면 예외를 던지고 `deposit` 또는 `보증금액`을 쉼표 제거 후 만원 단위 `Long`으로 파싱한다.

- [ ] **Step 7: 제공처 집중 테스트를 통과시킨다.**

Run: `./gradlew test --tests 'com.safelense.analysis.collection.*ClientTests' --tests 'com.safelense.analysis.collection.VWorldAddressResolverTests' --rerun-tasks`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: 두 번째 의미 단위를 커밋한다.**

```bash
git add src/main/kotlin/com/safelense/analysis/collection src/test/kotlin/com/safelense/analysis/collection
git commit -m "feat: 실제 주택 공공데이터 클라이언트 추가"
```

---

### Task 3: LIVE 수집기와 규칙 입력

**Files:**
- Create: `src/main/kotlin/com/safelense/analysis/collection/LivePropertyDataCollector.kt`
- Delete: `src/main/kotlin/com/safelense/analysis/collection/DemoPropertyDataCollector.kt`
- Delete: `src/test/kotlin/com/safelense/analysis/collection/DemoPropertyDataCollectorTests.kt`
- Modify: `src/main/kotlin/com/safelense/analysis/AnalysisRiskRuleEngine.kt`
- Modify: `src/main/kotlin/com/safelense/analysis/extraction/RegistryExtractor.kt`
- Test: `src/test/kotlin/com/safelense/analysis/collection/LivePropertyDataCollectorTests.kt`
- Modify: `src/test/kotlin/com/safelense/analysis/run/AnalysisRiskRuleEngineEvidenceTests.kt`
- Modify: `src/test/kotlin/com/safelense/analysis/extraction/RegistryExtractorTests.kt`

**Interfaces:**
- Produces: 단일 Spring `PropertyDataCollector` 빈인 `LivePropertyDataCollector`.
- Produces: `ADDRESS_RESOLUTION`, `BUILDING_REGISTER`, `OFFICIAL_PRICE`, `RENT_MARKET`과 명시적인 미지원 근거.
- Consumes: Task 2의 resolver와 세 provider client, `Clock`, `ObjectMapper`.

- [ ] **Step 1: LIVE 수집기의 성공·부분 실패·주소 실패 테스트를 작성한다.**

```kotlin
// 실제 제공처별 결과를 독립적인 정규화 근거로 만드는 수집기 테스트
@Test
fun `collects live evidence and marks unsupported fields explicitly`() {
    whenever(resolver.resolve(property.address)).thenReturn(address)
    whenever(buildingClient.fetch(address)).thenReturn(building)
    whenever(priceClient.fetch(address, 2026)).thenReturn(price)
    whenever(rentClient.fetch(eq(address), eq(BuildingType.APARTMENT), any()))
        .thenReturn(rent)

    val result = collector().collect(property)

    assertThat(result.map { it.evidenceKey }).containsExactly(
        "ADDRESS_RESOLUTION",
        "BUILDING_REGISTER",
        "OFFICIAL_PRICE",
        "RENT_MARKET",
        "URBAN_PLAN",
        "REDEVELOPMENT_PLAN",
        "FLOOD_HISTORY",
        "DEPOSIT_INSURANCE_ELIGIBILITY",
        "JEONSE_RATIO",
    )
    assertThat(result.first { it.evidenceKey == "OFFICIAL_PRICE" }.source)
        .isEqualTo("VWORLD_OFFICIAL_PRICE")
    assertThat(result.first { it.evidenceKey == "RENT_MARKET" }.valueJson)
        .doesNotContain("sale", "transactionPrice")
    assertThat(result.filter { it.evidenceKey in unsupportedKeys })
        .allMatch { it.status == EvidenceStatus.NOT_AVAILABLE }
}
```

부분 실패 테스트는 건축물대장 client만 예외를 던지고 `BUILDING_REGISTER`만 `UNAVAILABLE`, 나머지 실조회 결과는 `AVAILABLE`인지 검증한다. 주소 실패 테스트는 외부 client를 호출하지 않고 실조회 3개를 `UNAVAILABLE`로 반환하는지 검증한다.

- [ ] **Step 2: LIVE 수집기 테스트가 구현 부재로 실패하는지 확인한다.**

Run: `./gradlew test --tests 'com.safelense.analysis.collection.LivePropertyDataCollectorTests' --rerun-tasks`

Expected: `LivePropertyDataCollector` 부재로 컴파일 실패.

- [ ] **Step 3: 제공처별 예외를 격리하는 수집기를 구현한다.**

```kotlin
// 실제 주소와 공공 API 결과를 실행별 근거로 정규화하는 수집기
@Component
class LivePropertyDataCollector(
    private val addressResolver: PropertyAddressResolver,
    private val buildingClient: BuildingRegisterClient,
    private val priceClient: OfficialPriceClient,
    private val rentClient: RentMarketClient,
    private val objectMapper: ObjectMapper,
    private val clock: Clock = Clock.systemUTC(),
) : PropertyDataCollector {
    override fun collect(property: HomeProperty): List<CollectedEvidenceCommand> {
        val now = Instant.now(clock)
        val address = runCatching { addressResolver.resolve(property.address) }.getOrNull()
        if (address == null) {
            return unresolvedAddressEvidence(now)
        }
        return listOf(
            available("ADDRESS_RESOLUTION", "VWORLD_ADDRESS", address, now),
            collectBuilding(address, now),
            collectOfficialPrice(address, now),
            collectRent(address, property.buildingType, now),
        ) + unsupportedEvidence(now)
    }
}
```

각 `collect*` 함수는 정상 `null`을 `NOT_AVAILABLE`, 예외를 `UNAVAILABLE`로 변환한다. 최근 6개월은 현재 `YearMonth`부터 역순으로 계산한 뒤 오름차순으로 전달한다. `OFFICIAL_PRICE.valueJson`의 금액 필드는 기존 규칙 엔진 호환을 위해 `{"amount":50000,"unit":"MANWON","standardYear":2026}` 형태를 사용한다.

- [ ] **Step 4: 규칙 엔진이 공동주택가격만 사용하도록 회귀 테스트를 수정한다.**

```kotlin
@Test
fun `ignores rent market and former transaction price as property value`() {
    val evidence = listOf(
        evidence("TRANSACTION_PRICE", """{"amount":100000}"""),
        evidence("RENT_MARKET", """{"medianDepositManwon":30000}"""),
    )

    val result = engine.assess(property, evidence, objectMapper)

    assertThat(result.score).isNull()
    assertThat(result.grade).isEqualTo(AnalysisRiskGrade.UNKNOWN)
}
```

- [ ] **Step 5: 규칙 엔진 테스트 실패를 확인한 뒤 가격 키를 고정한다.**

Run: `./gradlew test --tests 'com.safelense.analysis.run.AnalysisRiskRuleEngineEvidenceTests' --rerun-tasks`

Expected: 기존 `TRANSACTION_PRICE` 우선 사용 때문에 실패.

`AnalysisRiskRuleEngine.assess(property, evidence, objectMapper)`는 `OFFICIAL_PRICE` 한 개만 찾아 `estimatedPropertyValueManwon`으로 전달하도록 바꾼다.

- [ ] **Step 6: 등기부 추출기 이름과 출처를 실제 동작에 맞춘다.**

`DemoRegistryExtractor`를 `RegistryDocumentStatusExtractor`로 바꾸고, source를 `REGISTRY_UPLOAD_STATUS`로 고정한다. 문서가 없으면 `NOT_AVAILABLE`, 추출 성공 상태면 `AVAILABLE`, 만료·실패 상태면 기존 상태 계약에 맞춰 `UNAVAILABLE`을 반환하며 OCR이나 권리 필드를 만들지 않는다.

- [ ] **Step 7: LIVE 수집·규칙·등기 상태 테스트를 통과시킨다.**

Run: `./gradlew test --tests 'com.safelense.analysis.collection.LivePropertyDataCollectorTests' --tests 'com.safelense.analysis.run.AnalysisRiskRuleEngineEvidenceTests' --tests 'com.safelense.analysis.extraction.RegistryExtractorTests' --rerun-tasks`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: 세 번째 의미 단위를 커밋한다.**

```bash
git add src/main/kotlin/com/safelense/analysis/collection src/main/kotlin/com/safelense/analysis/AnalysisRiskRuleEngine.kt src/main/kotlin/com/safelense/analysis/extraction/RegistryExtractor.kt src/test/kotlin/com/safelense/analysis/collection src/test/kotlin/com/safelense/analysis/run/AnalysisRiskRuleEngineEvidenceTests.kt src/test/kotlin/com/safelense/analysis/extraction/RegistryExtractorTests.kt
git commit -m "feat: 신규 분석을 실제 공공데이터 수집으로 전환"
```

---

### Task 4: OpenAI 임베딩과 일회성 XLSX import

**Files:**
- Create: `src/main/kotlin/com/safelense/analysis/match/EmbeddingClient.kt`
- Create: `src/main/kotlin/com/safelense/analysis/match/OpenAiEmbeddingClient.kt`
- Create: `src/main/kotlin/com/safelense/analysis/match/ConsultationCaseImportService.kt`
- Create: `src/main/kotlin/com/safelense/analysis/match/ConsultationCaseImportRunner.kt`
- Test: `src/test/kotlin/com/safelense/analysis/match/OpenAiEmbeddingClientTests.kt`
- Test: `src/test/kotlin/com/safelense/analysis/match/ConsultationCaseImportServiceTests.kt`

**Interfaces:**
- Produces: `EmbeddingClient.embed(inputs: List<String>): List<List<Double>>`.
- Produces: `ConsultationCaseImportService.import(path: Path): ConsultationImportResult`.
- Consumes: `ConsultationCaseRepository`, Apache POI, `OpenAiProperties`, Jackson.

- [ ] **Step 1: OpenAI 임베딩 HTTP 계약 테스트를 작성한다.**

```kotlin
// OpenAI Embeddings API 요청과 응답 순서를 검증하는 테스트
@Test
fun `creates embeddings without storing source text`() {
    server.enqueue(jsonResponse(
        """{"data":[{"index":0,"embedding":[0.1,0.2]},{"index":1,"embedding":[0.3,0.4]}]}""",
    ))

    val result = client().embed(listOf("첫 상담", "둘째 상담"))

    assertThat(result).containsExactly(listOf(0.1, 0.2), listOf(0.3, 0.4))
    val body = objectMapper.readTree(server.takeRequest().body.readUtf8())
    assertThat(body.get("model").asString()).isEqualTo("text-embedding-3-small")
    assertThat(body.get("input").size()).isEqualTo(2)
}
```

- [ ] **Step 2: 임베딩 테스트 실패를 확인하고 최소 HTTP 클라이언트를 구현한다.**

Run: `./gradlew test --tests 'com.safelense.analysis.match.OpenAiEmbeddingClientTests' --rerun-tasks`

Expected: `EmbeddingClient`와 구현 부재로 컴파일 실패.

```kotlin
// 상담 문장을 OpenAI 임베딩 벡터로 변환하는 경계
fun interface EmbeddingClient {
    fun embed(inputs: List<String>): List<List<Double>>
}
```

`OpenAiEmbeddingClient`는 `POST ${baseUrl}/embeddings`, Bearer 인증, JSON `{"model": embeddingModel, "input": inputs}`를 보내고 `data`를 `index`로 정렬한다. 입력 개수와 응답 벡터 개수가 다르거나 HTTP·파싱 오류가 나면 `EmbeddingUnavailableException`을 던진다. 요청 본문과 키는 로그에 남기지 않는다.

- [ ] **Step 3: 실제 XLSX 구조와 upsert를 고정하는 import 테스트를 작성한다.**

```kotlin
// 비식별 상담 XLSX를 검증하고 임베딩과 함께 upsert하는 테스트
@Test
fun `imports the named sheet and preserves nullable text`() {
    val workbook = workbookWith(
        headers = EXPECTED_HEADERS,
        rows = listOf(
            listOf(
                "1", "임차in", "2026-01", "서울특별시", "중구", "1억~2억",
                "계약전", "아파트", "근저당", "미가입", "보증금", "상담",
                "상황 요약", "", "", "비식별 변호사",
            ),
        ),
    )
    whenever(embeddingClient.embed(any())).thenReturn(listOf(listOf(0.1, 0.2)))

    val result = service().import(workbook.path)

    assertThat(result).isEqualTo(ConsultationImportResult(read = 1, upserted = 1, failed = 0))
    assertThat(saved.single().source).isEqualTo("DIVE_2026_COUNSELING")
    assertThat(saved.single().datasetVersion).isEqualTo("2026-v1")
    assertThat(saved.single().counselorOpinion).isNull()
    assertThat(saved.single().embeddingJson).isEqualTo("[0.1,0.2]")
}
```

헤더가 하나라도 다르면 적재 전 `InvalidConsultationWorkbookException`을 던지는 테스트와 같은 `(source, externalCaseId)`를 갱신하는 테스트를 함께 작성한다.

- [ ] **Step 4: import 테스트가 서비스 부재로 실패하는지 확인한다.**

Run: `./gradlew test --tests 'com.safelense.analysis.match.ConsultationCaseImportServiceTests' --rerun-tasks`

Expected: import 서비스와 결과 타입 부재로 컴파일 실패.

- [ ] **Step 5: XLSX 검증·배치 임베딩·upsert를 구현한다.**

```kotlin
// 임차인 상담 XLSX 적재 결과 건수를 표현하는 값
data class ConsultationImportResult(
    val read: Int,
    val upserted: Int,
    val failed: Int,
)
```

`ConsultationCaseImportService`는 `비식별_상담데이터` 시트와 다음 16개 헤더를 정확한 순서로 확인한다.

```kotlin
val expectedHeaders = listOf(
    "일련번호", "자료군", "상담월", "지역(시도)", "지역(시군구)", "보증금구간",
    "계약상태", "주택유형", "선순위권리", "보증보험", "분쟁유형", "진행단계",
    "상황요약", "담당자의견", "특이사항", "상담변호사",
)
```

빈 셀은 nullable 텍스트 세 필드에서만 `null`로 변환하고 나머지는 실패 행으로 센다. 임베딩 입력은 구조화 필드와 `상황요약`, `담당자의견`, `특이사항`을 줄바꿈으로 결합하되 상담변호사 값은 제외한다. 최대 100건씩 임베딩하고 성공 배치만 저장한다. 로그에는 읽은 수·upsert 수·실패 행 번호만 남기며 상담 텍스트와 키는 남기지 않는다.

`ConsultationCaseImportRunner`는 `app.consultation-import.file`이 있을 때만 생성되는 `ApplicationRunner`로 구현한다. 실행 명령은 다음과 같이 고정한다.

Run: `./gradlew bootRun --args='--spring.main.web-application-type=none --app.consultation-import.file=/absolute/path/to/비식별_임대차상담데이터.xlsx'`

- [ ] **Step 6: 임베딩과 importer 테스트를 통과시킨다.**

Run: `./gradlew test --tests 'com.safelense.analysis.match.OpenAiEmbeddingClientTests' --tests 'com.safelense.analysis.match.ConsultationCaseImportServiceTests' --rerun-tasks`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: 네 번째 의미 단위를 커밋한다.**

```bash
git add src/main/kotlin/com/safelense/analysis/match src/test/kotlin/com/safelense/analysis/match
git commit -m "feat: 임차인 상담 XLSX 임베딩 적재 추가"
```

---

### Task 5: 하이브리드 상담 검색과 실행별 스냅샷

**Files:**
- Modify: `src/main/kotlin/com/safelense/analysis/match/ConsultationCaseMatcher.kt`
- Create: `src/main/kotlin/com/safelense/analysis/match/HybridConsultationCaseMatcher.kt`
- Delete: `src/main/kotlin/com/safelense/analysis/match/DemoConsultationCaseMatcher.kt`
- Delete: `src/test/kotlin/com/safelense/analysis/match/DemoConsultationCaseMatcherTests.kt`
- Test: `src/test/kotlin/com/safelense/analysis/match/HybridConsultationCaseMatcherTests.kt`
- Test: `src/test/kotlin/com/safelense/analysis/match/ConsultationSimilarityTests.kt`

**Interfaces:**
- Produces: `ConsultationMatchRequest(property, evidence, assessment)`.
- Produces: `ConsultationCaseMatcher.match(request: ConsultationMatchRequest): ConsultationMatchResult`.
- Produces: `ConsultationMatchResult(cases: List<MatchedCase>, degraded: Boolean)`.
- Consumes: Task 1 저장소, Task 4 `EmbeddingClient`.

- [ ] **Step 1: 구조화 점수의 분모 제외와 결합 점수를 테스트한다.**

```kotlin
// 알려진 상담 조건만 분모에 포함하는 구조화 유사도 계산 테스트
@Test
fun `excludes unknown criteria from structured denominator`() {
    val query = ConsultationFeatures(
        depositBand = "1억~2억",
        housingType = "아파트",
        seniorRights = null,
        guaranteeStatus = null,
        province = "서울특별시",
    )

    val score = scorer.score(query, matchingCase)

    assertThat(score).isEqualTo((30.0 + 20.0 + 10.0) / 60.0)
}

@Test
fun `combines structured and semantic scores with fixed weights`() {
    assertThat(combine(structured = 0.8, semantic = 0.6)).isEqualTo(0.71)
}
```

- [ ] **Step 2: 구조화·결합 점수 테스트 실패를 확인하고 최소 계산기를 구현한다.**

Run: `./gradlew test --tests 'com.safelense.analysis.match.ConsultationSimilarityTests' --rerun-tasks`

Expected: 점수 계산 타입 부재로 컴파일 실패.

구조화 점수는 일치 가중치 합계를 알려진 입력 가중치 합계로 나눈다. 의미 점수는 cosine similarity를 `[-1,1]`에서 `[0,1]`로 `(cosine + 1) / 2` 정규화한다. 결합 함수는 `structured * 0.55 + semantic * 0.45`다.

- [ ] **Step 3: 상위 100개·상위 3개·임베딩 장애 fallback 테스트를 작성한다.**

```kotlin
// 구조화 후보를 좁힌 뒤 의미 점수와 결합해 상담 사례를 선택하는 테스트
@Test
fun `returns top three cases above threshold and snapshots scores`() {
    whenever(repository.findAll()).thenReturn(101Cases)
    whenever(embeddingClient.embed(listOf(anyQueryText))).thenReturn(listOf(queryVector))

    val result = matcher().match(request)

    assertThat(result.degraded).isFalse()
    assertThat(result.cases).hasSize(3)
    assertThat(result.cases.map { it.combinedScore }).isSortedAccordingTo(reverseOrder())
    verify(embeddingClient).embed(listOf(anyQueryText))
}

@Test
fun `uses structured only and marks degraded when embedding is unavailable`() {
    whenever(repository.findAll()).thenReturn(cases)
    whenever(embeddingClient.embed(any())).thenThrow(EmbeddingUnavailableException())

    val result = matcher().match(request)

    assertThat(result.degraded).isTrue()
    assertThat(result.cases).allMatch { it.semanticScore == null }
}
```

- [ ] **Step 4: matcher 테스트 실패를 확인하고 실제 matcher를 구현한다.**

Run: `./gradlew test --tests 'com.safelense.analysis.match.HybridConsultationCaseMatcherTests' --rerun-tasks`

Expected: `HybridConsultationCaseMatcher`와 새 계약 부재로 컴파일 실패.

```kotlin
// 공공 근거와 규칙 결과로 실제 상담 사례를 검색하는 요청
data class ConsultationMatchRequest(
    val property: HomeProperty,
    val evidence: List<CollectedEvidenceCommand>,
    val assessment: AnalysisRiskAssessment,
)

data class MatchedCase(
    val databaseId: Long,
    val caseId: String,
    val structuredScore: Double,
    val semanticScore: Double?,
    val combinedScore: Double,
    val pattern: String,
    val summary: String,
)

data class ConsultationMatchResult(
    val cases: List<MatchedCase>,
    val degraded: Boolean,
)

fun interface ConsultationCaseMatcher {
    fun match(request: ConsultationMatchRequest): ConsultationMatchResult
}
```

query feature는 매물 보증금에서 상담 데이터의 보증금구간을 만들고, `BuildingType`을 상담 한글 유형으로 매핑하며, `ADDRESS_RESOLUTION`에서 시도를 읽는다. 등기 권리와 보증보험 판정이 없으므로 두 필드는 `null`로 둔다. 구조화 점수 상위 100건만 임베딩 계산 대상으로 두고, 결합 점수 0.45 이상을 점수 내림차순·external case ID 오름차순으로 정렬해 3건을 반환한다.

요약은 `분쟁유형`, `진행단계`, 비식별 일반화 문장으로 만들고 API 응답에 원문 `상황요약`, `담당자의견`, `특이사항`을 직접 포함하지 않는다. LLM 입력용 `summary`에도 사람 이름과 주소를 포함하지 않는다.

- [ ] **Step 5: 하이브리드 검색 테스트를 통과시킨다.**

Run: `./gradlew test --tests 'com.safelense.analysis.match.*Tests' --rerun-tasks`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: 다섯 번째 의미 단위를 커밋한다.**

```bash
git add src/main/kotlin/com/safelense/analysis/match src/test/kotlin/com/safelense/analysis/match
git commit -m "feat: 실제 상담 하이브리드 검색 추가"
```

---

### Task 6: 워커 순서·LIVE 모드·유사 사례 리포트 통합

**Files:**
- Modify: `src/main/kotlin/com/safelense/analysis/run/AnalysisRunService.kt`
- Modify: `src/main/kotlin/com/safelense/analysis/run/AnalysisRunWorker.kt`
- Modify: `src/main/kotlin/com/safelense/analysis/report/ContractDecisionReportService.kt`
- Modify: `src/main/kotlin/com/safelense/analysis/interpretation/OpenAiReportInterpreter.kt`
- Modify: `src/main/kotlin/com/safelense/analysis/interpretation/ReportEvidenceValidator.kt`
- Modify: `src/main/kotlin/com/safelense/analysis/interpretation/OpenAiHttpReportClient.kt`
- Modify: `src/test/kotlin/com/safelense/analysis/run/AnalysisRunServiceTests.kt`
- Modify: `src/test/kotlin/com/safelense/analysis/run/AnalysisRunWorkerTests.kt`
- Modify: `src/test/kotlin/com/safelense/analysis/report/ContractDecisionReportServiceTests.kt`
- Modify: `src/test/kotlin/com/safelense/analysis/interpretation/OpenAiReportInterpreterTests.kt`
- Modify: `src/test/kotlin/com/safelense/analysis/interpretation/ReportEvidenceValidatorTests.kt`
- Modify: `src/test/kotlin/com/safelense/analysis/ContractDecisionEndToEndTests.kt`

**Interfaces:**
- Produces: 신규 실행 `dataMode=LIVE`.
- Produces: 실행별 `analysis_case_matches` 1~3건.
- Produces: `ContractDecisionReportView.similarCases`.
- Consumes: Task 5 matcher 결과와 Task 1 match 저장소.

- [ ] **Step 1: 신규 실행이 LIVE인지 확인하는 실패 테스트를 바꾼다.**

```kotlin
@Test
fun `creates a queued live analysis run`() {
    val created = service.create(userId = 1L, propertyId = 2L, idempotencyKey = "run-1", forceRefresh = false)
    assertThat(created.run.dataMode).isEqualTo(AnalysisDataMode.LIVE)
}
```

- [ ] **Step 2: LIVE 모드 테스트 실패를 확인한 뒤 생성 값을 바꾼다.**

Run: `./gradlew test --tests 'com.safelense.analysis.run.AnalysisRunServiceTests' --rerun-tasks`

Expected: 기존 `DEMO` 생성값 때문에 실패.

`AnalysisRunService.create`의 새 엔티티 생성값만 `AnalysisDataMode.LIVE`로 바꾸고 enum의 `DEMO`는 삭제하지 않는다.

- [ ] **Step 3: Rule Engine 뒤 검색과 스냅샷 저장 순서를 검증하는 워커 테스트를 작성한다.**

```kotlin
@Test
fun `assesses before matching and stores immutable case matches`() {
    val matcher = ConsultationCaseMatcher { request ->
        assertThat(request.assessment.ruleVersion).isEqualTo(ANALYSIS_RULE_VERSION)
        ConsultationMatchResult(listOf(matchedCase), degraded = false)
    }

    worker(matcher).execute(3L)

    verify(matchRepository).saveAll(argThat { matches ->
        matches.single().runId == 3L &&
            matches.single().consultationCaseId == matchedCase.databaseId &&
            matches.single().rank == 1
    })
}
```

임베딩 fallback으로 `degraded=true`이면 다른 근거가 모두 정상이어도 실행이 `PARTIAL`인지 함께 검증한다.

- [ ] **Step 4: 워커 테스트 실패를 확인하고 실행 순서와 저장을 바꾼다.**

Run: `./gradlew test --tests 'com.safelense.analysis.run.AnalysisRunWorkerTests' --rerun-tasks`

Expected: 기존 matcher 시그니처와 rule 이전 호출 때문에 컴파일 또는 assertion 실패.

워커는 `수집 → 등기 상태 → 근거 저장 → 규칙 평가 → 상담 검색 → 매치 저장 → 리포트` 순서로 실행한다. 공공 수집 전체 예외용 근거 source는 `PUBLIC_DATA_COLLECTION`, 상담 검색 예외는 빈 결과와 `degraded=true`로 변환한다. `providerUnavailable`, 근거의 실패 상태, 검색 degraded, AI fallback 중 하나라도 있으면 `PARTIAL`로 종료한다.

- [ ] **Step 5: 리포트 유사 사례와 case 근거 검증 테스트를 작성한다.**

```kotlin
@Test
fun `includes snapshotted similar cases in report`() {
    val generated = service.generate(run, evidence, listOf(matchedCase), assessment)

    assertThat(generated.view.similarCases).containsExactly(
        SimilarCaseReport(
            caseId = matchedCase.caseId,
            similarity = matchedCase.combinedScore,
            pattern = matchedCase.pattern,
            summary = matchedCase.summary,
        ),
    )
    assertThat(generated.view.dataMode).isEqualTo(AnalysisDataMode.LIVE)
}

@Test
fun `allows case ids but rejects numbers absent from the cited case`() {
    val values = mapOf("case-101" to "보증금 15000만원 분쟁")
    validator.validate(
        AiReportResult(summary = EvidenceBackedStatement("보증금 15000만원 유사 사례가 있습니다.", listOf("case-101"))),
        values,
    )
    assertThatThrownBy {
        validator.validate(
            AiReportResult(summary = EvidenceBackedStatement("보증금 20000만원 유사 사례가 있습니다.", listOf("case-101"))),
            values,
        )
    }.isInstanceOf(InvalidAiEvidenceException::class.java)
}
```

- [ ] **Step 6: 리포트 테스트 실패를 확인하고 case ID를 검증 범위에 넣는다.**

Run: `./gradlew test --tests 'com.safelense.analysis.report.ContractDecisionReportServiceTests' --tests 'com.safelense.analysis.interpretation.*Tests' --rerun-tasks`

Expected: `similarCases` 부재와 validator 허용 ID 부재로 실패.

```kotlin
@Schema(description = "실제 비식별 상담 데이터에서 검색한 유사 사례")
data class SimilarCaseReport(
    var caseId: String = "",
    var similarity: Double = 0.0,
    var pattern: String = "",
    var summary: String = "",
)
```

`ContractDecisionReportView`에 `var similarCases: List<SimilarCaseReport> = emptyList()`를 추가한다. `OpenAiReportInterpreter`는 validator 값 map에 `case-${matchedCase.caseId}`와 일반화된 `pattern + summary`를 추가한다. OpenAI 요청의 `MatchedCase`에는 원문이 없으며 case ID 인용 규칙을 instructions에 추가한다. fallback 문구는 기존 AVAILABLE evidence ID가 비어 있으면 case ID를 사용하고, 둘 다 없으면 리포트 생성 실패가 아니라 빈 근거 없는 안전한 고정 안내를 별도 처리하도록 기존 계약 테스트를 유지한다.

- [ ] **Step 7: 통합된 분석 테스트를 통과시킨다.**

Run: `./gradlew test --tests 'com.safelense.analysis.run.*Tests' --tests 'com.safelense.analysis.report.*Tests' --tests 'com.safelense.analysis.interpretation.*Tests' --tests 'com.safelense.analysis.ContractDecisionEndToEndTests' --rerun-tasks`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: 여섯 번째 의미 단위를 커밋한다.**

```bash
git add src/main/kotlin/com/safelense/analysis src/test/kotlin/com/safelense/analysis
git commit -m "feat: LIVE 분석과 유사 상담 리포트 통합"
```

---

### Task 7: 전체 검증과 실제 상담 적재

**Files:**
- Modify: `docs/work-notes/checklist.md`
- Modify: `docs/work-notes/context-notes.md`
- Verify only: `/Users/keemhoeyune/Downloads/비식별_임대차상담데이터.xlsx`

**Interfaces:**
- Consumes: Tasks 1~6의 완성 코드와 외부 XLSX.
- Produces: 통과한 전체 테스트·실행 JAR, 가능할 경우 PostgreSQL의 상담 938건.

- [ ] **Step 1: DEMO 구현과 실제 키 노출이 남지 않았는지 정적 검사한다.**

Run: `rg -n 'DemoPropertyDataCollector|DemoConsultationCaseMatcher|source = \"DEMO\"|DEMO-HUG|76DC2154|1da6c468' src docs --glob '!docs/superpowers/specs/**' --glob '!docs/superpowers/plans/**'`

Expected: 신규 실행 경로와 추적 파일에서 결과 없음. `AnalysisDataMode.DEMO` enum과 과거 호환 테스트는 허용한다.

- [ ] **Step 2: 상담 원본이 Git 추적 대상이 아닌지 확인한다.**

Run: `git ls-files '*.xlsx' '*.xls'`

Expected: 제공받은 상담 원본 경로가 출력되지 않음.

- [ ] **Step 3: 전체 테스트를 순차 실행한다.**

Run: `./gradlew test --rerun-tasks`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: 실행 JAR를 생성한다.**

Run: `./gradlew bootJar --rerun-tasks`

Expected: `BUILD SUCCESSFUL`이며 `build/libs/safelense-0.0.1-SNAPSHOT.jar` 생성.

- [ ] **Step 5: 실제 DB와 OpenAI 환경변수가 있는 경우 상담 938건을 적재한다.**

먼저 값 자체를 출력하지 않고 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `OPENAI_API_KEY`가 모두 설정됐는지만 검사한다. 모두 있으면 다음 명령을 실행한다.

Run: `./gradlew bootRun --args='--spring.main.web-application-type=none --app.consultation-import.file=/Users/keemhoeyune/Downloads/비식별_임대차상담데이터.xlsx'`

Expected: `read=938`, `upserted=938`, `failed=0`. 환경변수가 없으면 적재를 실행하지 않고 코드·fixture 검증만 완료한 것으로 기록한다.

- [ ] **Step 6: 공백과 변경 범위를 검토한다.**

Run: `git diff --check`

Expected: 출력 없음.

Run: `git status --short`

Expected: 계획된 문서 변경만 남거나 깨끗한 작업 트리.

- [ ] **Step 7: 검증 결과와 남은 운영 위험을 컨텍스트 노트에 기록한다.**

실제 provider API를 운영 키로 호출하지 못한 경우 fixture 기반 HTTP 계약만 검증했음을 기록한다. 실제 호출이 성공했으면 주소 하나에 대해 각 evidence status와 source만 기록하고 응답 원문·주소·키는 기록하지 않는다.

- [ ] **Step 8: 마지막 문서 변경을 커밋한다.**

```bash
git add docs/work-notes/checklist.md docs/work-notes/context-notes.md
git commit -m "docs: 실제 데이터 분석 검증 결과 기록"
```

## Self-Review

- Spec coverage: 신규 `LIVE`, 공공데이터 3계열, 독립 실패 격리, 미지원 항목, 전월세 비가격 처리, 등기 상태 한정, 임차in 출처·버전, 55:45 검색, 100개 후보·3개 결과·0.45 임계값, 실행별 스냅샷, case 근거 검증, XLSX 비커밋, SSM 키가 Tasks 1~7에 모두 연결돼 있다.
- Placeholder scan: 구현을 미루는 표현 없이 각 변경 파일, 인터페이스, 실패·성공 명령과 기대 결과를 명시했다.
- Type consistency: `ConsultationMatchRequest → ConsultationMatchResult → MatchedCase → AnalysisCaseMatch → SimilarCaseReport` 흐름과 `ResolvedPropertyAddress → provider snapshots → CollectedEvidenceCommand` 흐름의 이름과 필드를 전 Task에서 동일하게 사용했다.
