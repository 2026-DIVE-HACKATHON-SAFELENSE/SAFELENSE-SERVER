# Token Refresh Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 유효한 서비스 리프레시 JWT를 검증해 새 액세스 JWT를 발급한다.

**Architecture:** `JwtTokenIssuer`가 서명, 만료, `tokenType=refresh` claim을 검증한다. `TokenRefreshService`는 결과를 유스케이스 모델로 변환하고 컨트롤러는 `POST /api/v1/auth/refresh` 계약을 노출한다.

**Tech Stack:** Kotlin, Spring MVC, JJWT, JUnit 5, MockMvc.

## Global Constraints

- 요청 본문은 `refreshToken`이다.
- 응답은 새 액세스 토큰, `Bearer`, 액세스 토큰 만료 시간만 포함한다.
- 새 리프레시 토큰은 발급하지 않는다.
- 토큰 저장, 폐기, 로그아웃은 구현하지 않는다.

### Task 1: 리프레시 JWT 검증과 액세스 토큰 발급

**Files:**
- Modify: `src/main/kotlin/com/safelense/auth/application/JwtTokenIssuer.kt`
- Create: `src/main/kotlin/com/safelense/auth/application/TokenRefreshService.kt`
- Test: `src/test/kotlin/com/safelense/auth/application/JwtTokenIssuerTests.kt`

- [x] 유효한 리프레시 토큰이 같은 `sub`의 새 액세스 토큰을 만든다는 실패 테스트를 작성한다.
- [x] 액세스 토큰과 형식이 잘못된 토큰이 `InvalidRefreshTokenException`을 던진다는 실패 테스트를 작성한다.
- [x] `JwtTokenIssuer.refresh(refreshToken)`와 `TokenRefreshService.refresh(refreshToken)`를 최소 구현한다.
- [x] 유스케이스 테스트를 실행한다.

### Task 2: 토큰 재발급 HTTP API

**Files:**
- Modify: `src/main/kotlin/com/safelense/auth/presentation/KakaoAuthController.kt`
- Modify: `src/main/kotlin/com/safelense/auth/presentation/ApiExceptionHandler.kt`
- Modify: `src/main/kotlin/com/safelense/auth/config/SecurityConfig.kt`
- Test: `src/test/kotlin/com/safelense/auth/presentation/KakaoAuthControllerTests.kt`

- [x] 유효한 리프레시 토큰의 성공 응답과 빈 토큰의 `400`, 잘못된 토큰의 `401`을 검증하는 실패 테스트를 작성한다.
- [x] `POST /api/v1/auth/refresh`와 오류 매핑을 최소 구현한다.
- [x] MVC 테스트를 실행한다.

### Task 3: 전체 검증과 문서화

- [x] `./gradlew --no-daemon -Dkotlin.compiler.execution.strategy=in-process -Dkotlin.incremental=false test bootJar`를 실행한다.
- [x] 작업 노트와 변경 사항을 검토한다.
- [x] 기능과 문서를 별도 한글 커밋으로 기록한다.
