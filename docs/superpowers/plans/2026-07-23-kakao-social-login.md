# Kakao Social Login Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 카카오 인가 코드로 MySQL 사용자를 로그인 또는 가입시키고 서비스 JWT 쌍을 발급한다.

**Architecture:** `KakaoApiClient`는 카카오 REST API를 호출하고 `KakaoLoginService`는 사용자 조회·생성과 토큰 발급을 조합한다. 컨트롤러는 명세 경로의 요청과 응답만 처리하며 Flyway가 MySQL 사용자 스키마를 소유한다.

**Tech Stack:** Kotlin, Spring Boot, Spring MVC, Spring Data JPA, Spring Security, Flyway MySQL, JJWT, MySQL, JUnit 5, MockMvc.

## Global Constraints

- API는 `POST /api/v1/auth/kakao`이며 비인증 API다.
- 요청 본문은 `authorizationCode`와 `redirectUri`다.
- 사용자 식별자는 카카오 회원번호이며 MySQL `users.kakao_id`는 유일해야 한다.
- 비밀값은 환경 변수로만 주입한다.
- 토큰 갱신과 로그아웃은 구현하지 않는다.

---

## File Structure

- `build.gradle.kts`는 보안, 유효성 검사, Flyway, JWT 의존성을 선언한다.
- `src/main/resources/db/migration/V1__create_users.sql`은 MySQL 사용자 테이블을 만든다.
- `auth/config`는 구성 속성과 HTTP 보안을 소유한다.
- `auth/kakao`는 카카오 HTTP 통신만 소유한다.
- `auth/application`은 로그인 유스케이스와 JWT 발급을 소유한다.
- `user`는 JPA 사용자 엔티티와 저장소를 소유한다.
- `auth/presentation`은 HTTP 계약과 오류 응답을 소유한다.

### Task 1: 빌드와 MySQL 사용자 스키마

**Files:**
- Modify: `build.gradle.kts`
- Modify: `src/main/resources/application.yml`
- Create: `src/main/resources/db/migration/V1__create_users.sql`

**Interfaces:**
- Produces: MySQL `users` 테이블과 `auth.kakao`, `auth.jwt` 구성 값.

- [ ] **Step 1: 실패하는 마이그레이션 존재 테스트를 작성한다.**

```kotlin
@Test
fun `users migration exists`() {
    assertThat(ClassPathResource("db/migration/V1__create_users.sql").exists()).isTrue()
}
```

- [ ] **Step 2: 테스트가 실패하는지 확인한다.**

Run: `./gradlew test --tests '*MigrationTests'`

Expected: `V1__create_users.sql`을 찾지 못해 실패한다.

- [ ] **Step 3: 의존성과 마이그레이션을 추가한다.**

```kotlin
implementation("org.springframework.boot:spring-boot-starter-security")
implementation("org.springframework.boot:spring-boot-starter-validation")
implementation("org.flywaydb:flyway-mysql")
implementation("io.jsonwebtoken:jjwt-api:0.12.6")
runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")
```

```sql
CREATE TABLE users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    kakao_id BIGINT NOT NULL,
    nickname VARCHAR(255) NOT NULL,
    profile_image_url VARCHAR(2048) NULL,
    onboarding_completed BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_users_kakao_id UNIQUE (kakao_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

- [ ] **Step 4: 테스트를 통과시키고 컴파일한다.**

Run: `./gradlew test --tests '*MigrationTests' compileKotlin`

Expected: `BUILD SUCCESSFUL`.

### Task 2: 사용자 영속성과 JWT 발급기

**Files:**
- Create: `src/main/kotlin/com/safelense/user/User.kt`
- Create: `src/main/kotlin/com/safelense/user/UserRepository.kt`
- Create: `src/main/kotlin/com/safelense/auth/config/JwtProperties.kt`
- Create: `src/main/kotlin/com/safelense/auth/application/JwtTokenIssuer.kt`
- Test: `src/test/kotlin/com/safelense/auth/application/JwtTokenIssuerTests.kt`

**Interfaces:**
- Produces: `UserRepository.findByKakaoId(kakaoId: Long): User?`.
- Produces: `JwtTokenIssuer.issue(userId: Long): IssuedTokens`.

- [ ] **Step 1: 만료 시간이 서로 다른 JWT 쌍을 기대하는 테스트를 작성한다.**

```kotlin
@Test
fun `issues access and refresh tokens for a user`() {
    val tokens = issuer.issue(42L)

    assertThat(tokens.accessToken).isNotBlank()
    assertThat(tokens.refreshToken).isNotBlank()
    assertThat(tokens.expiresIn).isEqualTo(1800)
}
```

- [ ] **Step 2: 테스트가 `JwtTokenIssuer` 부재로 실패하는지 확인한다.**

Run: `./gradlew test --tests '*JwtTokenIssuerTests'`

Expected: 컴파일 실패.

- [ ] **Step 3: 최소 영속성과 토큰 발급기를 구현한다.**

```kotlin
interface UserRepository : JpaRepository<User, Long> {
    fun findByKakaoId(kakaoId: Long): User?
}

data class IssuedTokens(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long,
)

fun issue(userId: Long): IssuedTokens
```

JWT access claim에는 `sub`와 `tokenType=access`, refresh claim에는 `sub`와 `tokenType=refresh`를 넣고, `JwtProperties`의 만료 시간으로 각각 서명한다.

- [ ] **Step 4: 토큰 테스트를 통과시킨다.**

Run: `./gradlew test --tests '*JwtTokenIssuerTests'`

Expected: `BUILD SUCCESSFUL`.

### Task 3: 카카오 REST 어댑터와 로그인 유스케이스

**Files:**
- Create: `src/main/kotlin/com/safelense/auth/kakao/KakaoApiClient.kt`
- Create: `src/main/kotlin/com/safelense/auth/kakao/KakaoHttpApiClient.kt`
- Create: `src/main/kotlin/com/safelense/auth/kakao/KakaoProperties.kt`
- Create: `src/main/kotlin/com/safelense/auth/application/KakaoLoginService.kt`
- Test: `src/test/kotlin/com/safelense/auth/application/KakaoLoginServiceTests.kt`

**Interfaces:**
- Consumes: `UserRepository.findByKakaoId`, `JwtTokenIssuer.issue`.
- Produces: `KakaoLoginService.login(authorizationCode: String, redirectUri: String): KakaoLoginResult`.

- [ ] **Step 1: 신규 가입과 기존 로그인 테스트를 작성한다.**

```kotlin
@Test
fun `creates a user then returns issued tokens for a new Kakao member`() {
    whenever(kakaoApiClient.getUser("code", "https://client/callback"))
        .thenReturn(KakaoUser(123L, "라이언", "https://image"))
    whenever(userRepository.findByKakaoId(123L)).thenReturn(null)
    whenever(userRepository.save(any<User>())).thenAnswer { it.arguments[0] as User }

    val result = service.login("code", "https://client/callback")

    assertThat(result.isNewUser).isTrue()
    verify(userRepository).save(argThat { kakaoId == 123L })
}
```

- [ ] **Step 2: 유스케이스 테스트가 컴파일 실패하는지 확인한다.**

Run: `./gradlew test --tests '*KakaoLoginServiceTests'`

Expected: 로그인 유스케이스와 카카오 클라이언트 타입 부재로 실패한다.

- [ ] **Step 3: 카카오 HTTP 통신과 사용자 upsert 유스케이스를 구현한다.**

```kotlin
interface KakaoApiClient {
    fun getUser(authorizationCode: String, redirectUri: String): KakaoUser
}

data class KakaoUser(val id: Long, val nickname: String, val profileImageUrl: String?)

data class KakaoLoginResult(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long,
    val isNewUser: Boolean,
)
```

`KakaoHttpApiClient`은 `application/x-www-form-urlencoded` 형식으로 `grant_type=authorization_code`, REST API 키, 클라이언트 시크릿, 리다이렉트 URI와 인가 코드를 전송한다. 응답 access token으로 `https://kapi.kakao.com/v2/user/me`을 호출한다. 카카오 4xx는 `KakaoAuthenticationException`, 5xx와 역직렬화 실패는 `KakaoApiUnavailableException`으로 변환한다.

- [ ] **Step 4: 유스케이스 테스트를 통과시킨다.**

Run: `./gradlew test --tests '*KakaoLoginServiceTests'`

Expected: `BUILD SUCCESSFUL`.

### Task 4: HTTP 계약과 보안 설정

**Files:**
- Create: `src/main/kotlin/com/safelense/auth/presentation/KakaoAuthController.kt`
- Create: `src/main/kotlin/com/safelense/auth/presentation/ApiExceptionHandler.kt`
- Create: `src/main/kotlin/com/safelense/auth/config/SecurityConfig.kt`
- Modify: `src/main/kotlin/com/safelense/SafelenseApplication.kt`
- Test: `src/test/kotlin/com/safelense/auth/presentation/KakaoAuthControllerTests.kt`

**Interfaces:**
- Consumes: `KakaoLoginService.login(String, String): KakaoLoginResult`.
- Produces: `POST /api/v1/auth/kakao` with JSON token response.

- [ ] **Step 1: 요청 검증과 성공 응답을 기대하는 MVC 테스트를 작성한다.**

```kotlin
mockMvc.perform(
    post("/api/v1/auth/kakao")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"authorizationCode\":\"code\",\"redirectUri\":\"https://client/callback\"}"),
).andExpect(status().isOk)
    .andExpect(jsonPath("$.accessToken").value("access-token"))
    .andExpect(jsonPath("$.tokenType").value("Bearer"))

mockMvc.perform(post("/api/v1/auth/kakao").contentType(MediaType.APPLICATION_JSON).content("{}"))
    .andExpect(status().isBadRequest)
```

- [ ] **Step 2: MVC 테스트가 컨트롤러 부재로 실패하는지 확인한다.**

Run: `./gradlew test --tests '*KakaoAuthControllerTests'`

Expected: 컴파일 실패.

- [ ] **Step 3: 컨트롤러, 오류 응답과 보안을 구현한다.**

```kotlin
data class KakaoLoginRequest(
    @field:NotBlank val authorizationCode: String,
    @field:NotBlank val redirectUri: String,
)

data class KakaoLoginResponse(
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String = "Bearer",
    val expiresIn: Long,
    val isNewUser: Boolean,
)
```

Security 설정은 `POST /api/v1/auth/kakao`에 `permitAll()`을 적용하고 CSRF, 폼 로그인, HTTP Basic과 서버 세션을 비활성화한다. 애플리케이션 시작 클래스에는 `@ConfigurationPropertiesScan`을 추가한다.

- [ ] **Step 4: MVC 테스트를 통과시킨다.**

Run: `./gradlew test --tests '*KakaoAuthControllerTests'`

Expected: `BUILD SUCCESSFUL`.

### Task 5: 전체 회귀 확인

**Files:**
- Modify: `docs/work-notes/checklist.md`
- Modify: `docs/work-notes/context-notes.md`

- [ ] **Step 1: 전체 테스트와 빌드를 실행한다.**

Run: `./gradlew test bootJar`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: 환경 변수 목록을 검토한다.**

필수 환경 변수는 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `KAKAO_REST_API_KEY`, `KAKAO_CLIENT_SECRET`, `JWT_SECRET`이다.

- [ ] **Step 3: 구현 결과와 검증 결과를 작업 노트에 기록한다.**

- [ ] **Step 4: 변경 파일만 스테이징하고 커밋한다.**

Run: `git add build.gradle.kts src docs/work-notes && git commit -m "feat: add Kakao social login"`

Expected: 카카오 로그인 변경만 포함된 커밋 하나가 생성된다.

## Self-Review

- 명세의 경로, 메서드, 요청 필드, 비인증 조건과 JWT 발급 요구는 Task 3과 Task 4에서 다룬다.
- MySQL 사용자 저장과 유니크 카카오 식별자는 Task 1과 Task 2에서 다룬다.
- 카카오 API 오류 매핑은 Task 3과 Task 4에서 다룬다.
- 토큰 갱신과 로그아웃은 어떤 작업에도 포함하지 않는다.
