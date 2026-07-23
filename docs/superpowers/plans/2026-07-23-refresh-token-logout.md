# 리프레시 토큰 저장과 로그아웃 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 리프레시 JWT를 MySQL에 해시로 저장·교체하고 액세스 JWT 기반 로그아웃으로 삭제한다.

**Architecture:** `RefreshTokenStore`가 사용자별 리프레시 JWT 해시를 저장·검증·삭제한다. 로그인과 갱신 서비스가 토큰 발급 뒤 저장소를 갱신하며, `JwtAuthenticationFilter`가 액세스 JWT를 검증해 로그아웃 요청의 사용자 ID를 Spring Security에 전달한다.

**Tech Stack:** Kotlin, Spring Boot, Spring Security, Spring Data JPA, Flyway, JJWT, JUnit 5, Mockito, MockMvc, MySQL.

## Global Constraints

- 리프레시 토큰 원문은 저장하지 않고 SHA-256 해시만 저장한다.
- 사용자당 활성 리프레시 토큰은 하나다.
- 갱신 성공 시 액세스·리프레시 JWT를 모두 새로 발급하고 DB 저장 값을 교체한다.
- `POST /api/v1/auth/logout`은 Bearer 액세스 JWT가 필요하며 성공 시 `204 No Content`를 반환한다.
- 새 Kotlin 소스 첫 줄에는 역할을 설명하는 한국어 주석을 둔다.

---

### Task 1: 리프레시 토큰 영속성과 해시 저장소

**Files:**
- Create: `src/main/resources/db/migration/V2__create_refresh_tokens.sql`
- Create: `src/main/kotlin/com/safelense/auth/token/RefreshToken.kt`
- Create: `src/main/kotlin/com/safelense/auth/token/RefreshTokenRepository.kt`
- Create: `src/main/kotlin/com/safelense/auth/token/RefreshTokenStore.kt`
- Modify: `src/test/kotlin/com/safelense/user/UserMigrationTests.kt`
- Create: `src/test/kotlin/com/safelense/auth/token/RefreshTokenStoreTests.kt`

**Interfaces:** `RefreshTokenStore.save(userId: Long, refreshToken: String, expiresAt: Instant)`, `matches(userId: Long, refreshToken: String): Boolean`, `deleteByUserId(userId: Long)`을 제공한다.

- [x] **Step 1: 저장·일치·삭제의 실패 테스트를 작성한다.**

```kotlin
@Test
fun `stores a SHA-256 hash for a refresh token`() {
    store.save(7L, "refresh-token", expiresAt)
    val saved = argumentCaptor<RefreshToken>()
    verify(repository).save(saved.capture())
    assertThat(saved.firstValue.tokenHash).isNotEqualTo("refresh-token")
}

@Test
fun `matches only the stored refresh token`() {
    `when`(repository.findByUserId(7L)).thenReturn(RefreshToken(1L, 7L, sha256("refresh-token"), expiresAt))
    assertThat(store.matches(7L, "refresh-token")).isTrue()
    assertThat(store.matches(7L, "other-token")).isFalse()
}
```

- [x] **Step 2: 실패를 확인한다.**

Run: `./gradlew --no-daemon -Dkotlin.compiler.execution.strategy=in-process -Dkotlin.incremental=false test --tests com.safelense.auth.token.RefreshTokenStoreTests`.

Expected: `RefreshTokenStore`와 `RefreshToken`을 찾을 수 없어 컴파일 실패.

- [x] **Step 3: 마이그레이션과 최소 저장소를 구현한다.**

```sql
-- 사용자별 활성 리프레시 토큰 해시를 저장하는 MySQL 테이블
CREATE TABLE refresh_tokens (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    token_hash CHAR(64) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_refresh_tokens_user_id UNIQUE (user_id),
    CONSTRAINT fk_refresh_tokens_user_id FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

```kotlin
// 리프레시 토큰 해시를 저장하고 검증하는 유스케이스 저장소
@Service
class RefreshTokenStore(private val repository: RefreshTokenRepository) {
    fun save(userId: Long, refreshToken: String, expiresAt: Instant) {
        val existing = repository.findByUserId(userId)
        repository.save(existing?.apply {
            tokenHash = sha256(refreshToken)
            this.expiresAt = expiresAt
        } ?: RefreshToken(userId = userId, tokenHash = sha256(refreshToken), expiresAt = expiresAt))
    }
    fun matches(userId: Long, refreshToken: String): Boolean =
        repository.findByUserId(userId)?.tokenHash == sha256(refreshToken)
    fun deleteByUserId(userId: Long) = repository.deleteByUserId(userId)
}
```

- [x] **Step 4: 저장소와 마이그레이션 테스트를 통과시킨다.**

Run: `./gradlew --no-daemon -Dkotlin.compiler.execution.strategy=in-process -Dkotlin.incremental=false test --tests com.safelense.auth.token.RefreshTokenStoreTests --tests com.safelense.user.UserMigrationTests`.

Expected: `BUILD SUCCESSFUL`.

- [x] **Step 5: 영속성 변경을 커밋한다.**

```bash
git add src/main/resources/db/migration/V2__create_refresh_tokens.sql src/main/kotlin/com/safelense/auth/token src/test/kotlin/com/safelense/auth/token src/test/kotlin/com/safelense/user/UserMigrationTests.kt
git commit -m "feat: 리프레시 토큰 저장소 추가"
```

### Task 2: 로그인·갱신 토큰 교체

**Files:**
- Modify: `src/main/kotlin/com/safelense/auth/application/JwtTokenIssuer.kt`
- Modify: `src/main/kotlin/com/safelense/auth/application/KakaoLoginService.kt`
- Modify: `src/main/kotlin/com/safelense/auth/application/TokenRefreshService.kt`
- Modify: `src/main/kotlin/com/safelense/auth/presentation/KakaoAuthController.kt`
- Modify: `src/test/kotlin/com/safelense/auth/application/JwtTokenIssuerTests.kt`
- Modify: `src/test/kotlin/com/safelense/auth/application/KakaoLoginServiceTests.kt`
- Create: `src/test/kotlin/com/safelense/auth/application/TokenRefreshServiceTests.kt`
- Modify: `src/test/kotlin/com/safelense/auth/presentation/KakaoAuthControllerTests.kt`

**Interfaces:** `JwtTokenIssuer.validateRefreshToken(refreshToken: String): Long`과 `IssuedTokens.refreshTokenExpiresAt: Instant`를 추가한다. `TokenRefreshResult`는 `accessToken`, `refreshToken`, `expiresIn`을 반환한다.

- [x] **Step 1: 로그인 저장과 갱신 교체의 실패 테스트를 작성한다.**

```kotlin
@Test
fun `stores the issued refresh token after Kakao login`() {
    service.login("code", "https://client.example.com/callback")
    verify(refreshTokenStore).save(7L, "refresh", refreshExpiresAt)
}

@Test
fun `rotates tokens only when the stored token matches`() {
    `when`(issuer.validateRefreshToken("old-refresh")).thenReturn(7L)
    `when`(refreshTokenStore.matches(7L, "old-refresh")).thenReturn(true)
    `when`(issuer.issue(7L)).thenReturn(IssuedTokens("new-access", "new-refresh", 1800, refreshExpiresAt))
    assertThat(service.refresh("old-refresh").refreshToken).isEqualTo("new-refresh")
}
```

- [x] **Step 2: 실패를 확인한다.**

Run: `./gradlew --no-daemon -Dkotlin.compiler.execution.strategy=in-process -Dkotlin.incremental=false test --tests com.safelense.auth.application.KakaoLoginServiceTests --tests com.safelense.auth.application.TokenRefreshServiceTests`.

Expected: 새 생성자 인자, `validateRefreshToken`, 새 응답 필드가 없어 컴파일 실패.

- [x] **Step 3: 최소 토큰 교체 로직을 구현한다.**

```kotlin
fun refresh(refreshToken: String): TokenRefreshResult {
    val userId = jwtTokenIssuer.validateRefreshToken(refreshToken)
    if (!refreshTokenStore.matches(userId, refreshToken)) throw InvalidRefreshTokenException()
    val tokens = jwtTokenIssuer.issue(userId)
    refreshTokenStore.save(userId, tokens.refreshToken, tokens.refreshTokenExpiresAt)
    return TokenRefreshResult(tokens.accessToken, tokens.refreshToken, tokens.expiresIn)
}
```

`KakaoLoginService.login`은 `tokenIssuer.issue` 직후 `refreshTokenStore.save`를 호출한다. 재발급 응답은 `refreshToken`을 포함한다.

- [x] **Step 4: 단위·MVC 테스트를 통과시킨다.**

Run: `./gradlew --no-daemon -Dkotlin.compiler.execution.strategy=in-process -Dkotlin.incremental=false test --tests com.safelense.auth.application.JwtTokenIssuerTests --tests com.safelense.auth.application.KakaoLoginServiceTests --tests com.safelense.auth.application.TokenRefreshServiceTests --tests com.safelense.auth.presentation.KakaoAuthControllerTests`.

Expected: `BUILD SUCCESSFUL`.

- [x] **Step 5: 토큰 교체 변경을 커밋한다.**

```bash
git add src/main/kotlin/com/safelense/auth/application src/main/kotlin/com/safelense/auth/presentation/KakaoAuthController.kt src/test/kotlin/com/safelense/auth/application src/test/kotlin/com/safelense/auth/presentation/KakaoAuthControllerTests.kt
git commit -m "feat: 리프레시 토큰 교체 발급 추가"
```

### Task 3: 액세스 JWT 인증과 로그아웃 API

**Files:**
- Create: `src/main/kotlin/com/safelense/auth/application/LogoutService.kt`
- Create: `src/main/kotlin/com/safelense/auth/config/JwtAuthenticationFilter.kt`
- Modify: `src/main/kotlin/com/safelense/auth/config/SecurityConfig.kt`
- Modify: `src/main/kotlin/com/safelense/auth/application/JwtTokenIssuer.kt`
- Modify: `src/main/kotlin/com/safelense/auth/presentation/KakaoAuthController.kt`
- Create: `src/test/kotlin/com/safelense/auth/application/LogoutServiceTests.kt`
- Modify: `src/test/kotlin/com/safelense/auth/application/JwtTokenIssuerTests.kt`
- Modify: `src/test/kotlin/com/safelense/auth/presentation/KakaoAuthControllerTests.kt`

**Interfaces:** `JwtTokenIssuer.validateAccessToken(accessToken: String): Long`과 `LogoutService.logout(userId: Long)`을 추가한다. 필터는 `Authentication.principal`에 `Long` 사용자 ID를 저장한다.

- [x] **Step 1: 로그아웃 삭제와 액세스 토큰 검증의 실패 테스트를 작성한다.**

```kotlin
@Test
fun `deletes the current users refresh token on logout`() {
    logoutService.logout(7L)
    verify(refreshTokenStore).deleteByUserId(7L)
}

@Test
fun `rejects a refresh token as access authentication`() {
    assertThatThrownBy { issuer.validateAccessToken(issued.refreshToken) }
        .isInstanceOf(InvalidAccessTokenException::class.java)
}
```

- [x] **Step 2: 실패를 확인한다.**

Run: `./gradlew --no-daemon -Dkotlin.compiler.execution.strategy=in-process -Dkotlin.incremental=false test --tests com.safelense.auth.application.LogoutServiceTests --tests com.safelense.auth.application.JwtTokenIssuerTests`.

Expected: `LogoutService`와 `validateAccessToken`이 없어 컴파일 실패.

- [x] **Step 3: 인증 필터와 로그아웃 엔드포인트를 최소 구현한다.**

```kotlin
// Bearer 액세스 JWT에서 사용자 인증 정보를 만드는 보안 필터
class JwtAuthenticationFilter(private val issuer: JwtTokenIssuer) : OncePerRequestFilter() {
    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val token = request.getHeader(HttpHeaders.AUTHORIZATION)?.removePrefix("Bearer ")
        if (token != null) {
            val userId = issuer.validateAccessToken(token)
            SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(userId, null, emptyList())
        }
        chain.doFilter(request, response)
    }
}
```

`SecurityConfig`은 필터를 `UsernamePasswordAuthenticationFilter` 앞에 추가하고 인증 실패를 `401`로 변환한다. 컨트롤러는 `@PostMapping("/logout")`에서 `Authentication.principal as Long`을 받아 `logoutService.logout`을 호출한 뒤 `204`를 반환한다.

- [x] **Step 4: 서비스·JWT·MVC 테스트를 통과시킨다.**

Run: `./gradlew --no-daemon -Dkotlin.compiler.execution.strategy=in-process -Dkotlin.incremental=false test --tests com.safelense.auth.application.LogoutServiceTests --tests com.safelense.auth.application.JwtTokenIssuerTests --tests com.safelense.auth.presentation.KakaoAuthControllerTests`.

Expected: `BUILD SUCCESSFUL`.

- [x] **Step 5: 로그아웃 API 변경을 커밋한다.**

```bash
git add src/main/kotlin/com/safelense/auth/application/LogoutService.kt src/main/kotlin/com/safelense/auth/config src/main/kotlin/com/safelense/auth/presentation/KakaoAuthController.kt src/main/kotlin/com/safelense/auth/application/JwtTokenIssuer.kt src/test/kotlin/com/safelense/auth/application src/test/kotlin/com/safelense/auth/presentation/KakaoAuthControllerTests.kt
git commit -m "feat: JWT 로그아웃 API 추가"
```

### Task 4: 전체 검증과 문서 정리

**Files:**
- Modify: `docs/work-notes/checklist.md`
- Modify: `docs/work-notes/context-notes.md`
- Modify: `docs/superpowers/specs/2026-07-23-kakao-social-login-design.md`

- [x] **Step 1: 전체 테스트와 실행 JAR을 검증한다.**

Run: `./gradlew --no-daemon -Dkotlin.compiler.execution.strategy=in-process -Dkotlin.incremental=false test bootJar`.

Expected: `BUILD SUCCESSFUL`.

- [x] **Step 2: 작업 노트와 인증 설계를 갱신한다.**

체크리스트의 리프레시 토큰 저장과 로그아웃 항목을 완료 처리하고, 인증 설계 문서에 DB 해시 저장, 토큰 교체, 로그아웃 정책을 반영한다.

- [x] **Step 3: 문서를 커밋한다.**

```bash
git add docs
git commit -m "docs: 토큰 저장과 로그아웃 정책 반영"
```
