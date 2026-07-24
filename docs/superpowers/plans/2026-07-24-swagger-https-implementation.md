# Swagger HTTPS 공개 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `https://safelense.p-e.kr`에서 공개 Swagger UI와 OpenAPI JSON을 제공하고 HTTPS 프록시 주소를 Try it out 요청에 반영한다.

**Architecture:** Springdoc Web MVC UI starter가 기존 Spring MVC controller를 OpenAPI 문서와 Swagger UI로 자동 노출한다. Security는 문서 resource만 익명으로 허용하고, Spring의 forwarded-header 처리가 HTTPS 프록시의 외부 주소를 OpenAPI 요청 URL에 반영한다.

**Tech Stack:** Kotlin 2.3.10, Spring Boot 4.1.0, Spring Security, springdoc-openapi Web MVC UI 3.0.3.

## Global Constraints

- Spring Boot 4에는 `org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.3`만 사용한다.
- 공개 경로는 `/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html`로 한정한다.
- 업무 API의 기존 JWT 인증과 CORS 허용 출처는 변경하지 않는다.
- `https://safelense.p-e.kr`의 TLS 종료는 인프라가 담당하며 애플리케이션은 forwarded header만 처리한다.

---

### Task 1: Swagger 의존성·HTTPS 프록시·공개 경로 계약

**Files:**
- Modify: `build.gradle.kts`
- Modify: `src/main/resources/application.yml`
- Modify: `src/main/kotlin/com/safelense/auth/config/SecurityConfig.kt`
- Create: `src/test/kotlin/com/safelense/openapi/OpenApiContractTests.kt`

**Interfaces:**
- Consumes: Spring MVC controller mappings, HTTP request `X-Forwarded-Proto`와 `X-Forwarded-Host` headers.
- Produces: `/v3/api-docs`, `/swagger-ui/index.html` 공개 접근과 HTTPS 외부 URL 인식.

- [x] **Step 1: 정적 계약 테스트를 작성한다.**

```kotlin
// Springdoc 의존성, HTTPS forwarded header, 공개 문서 경로 계약을 검증하는 테스트
package com.safelense.openapi

import java.nio.file.Files
import java.nio.file.Path
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class OpenApiContractTests {
    @Test
    fun `uses the Spring Boot 4 compatible Swagger UI starter`() {
        assertThat(Files.readString(Path.of("build.gradle.kts")))
            .contains("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.3")
    }

    @Test
    fun `honors HTTPS proxy headers and exposes only documentation without authentication`() {
        val application = Files.readString(Path.of("src/main/resources/application.yml"))
        val security = Files.readString(Path.of("src/main/kotlin/com/safelense/auth/config/SecurityConfig.kt"))

        assertThat(application).contains("forward-headers-strategy: framework")
        assertThat(security).contains("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
    }
}
```

- [x] **Step 2: 계약 테스트가 실패하는지 확인한다.**

Run: `./gradlew test --tests 'com.safelense.openapi.OpenApiContractTests' --rerun-tasks`

Expected: `OpenApiContractTests` 또는 springdoc 의존성·forwarded header·문서 경로 계약이 없어 실패한다.

- [x] **Step 3: 최소 Swagger와 HTTPS 구성을 구현한다.**

`build.gradle.kts`의 dependencies에 다음 행을 추가한다.

```kotlin
implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.3")
```

`src/main/resources/application.yml`의 최상위에 다음 구성을 추가한다.

```yaml
server:
  forward-headers-strategy: framework
```

`SecurityConfig.securityFilterChain`의 권한 규칙 앞에 다음 행을 추가한다.

```kotlin
it.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
```

- [x] **Step 4: 계약 및 기존 CORS 테스트를 통과시킨다.**

Run: `./gradlew test --tests 'com.safelense.openapi.OpenApiContractTests' --tests 'com.safelense.auth.config.SecurityConfigCorsTests' --rerun-tasks`

Expected: `BUILD SUCCESSFUL`이며 문서 경로 외의 기존 인증 정책은 변경되지 않는다.

- [x] **Step 5: 논리적 커밋을 만든다.**

Run: `git add build.gradle.kts src/main/resources/application.yml src/main/kotlin/com/safelense/auth/config/SecurityConfig.kt src/test/kotlin/com/safelense/openapi/OpenApiContractTests.kt && git commit -m "feat: HTTPS Swagger UI 공개"`

Expected: Swagger 및 HTTPS forwarded-header 변경만 포함한 커밋이 생성된다.

### Task 2: 전체 검증·배포 확인

**Files:**
- Modify: `docs/work-notes/checklist.md`
- Modify: `docs/work-notes/context-notes.md`

**Interfaces:**
- Consumes: Task 1의 Swagger 공개 경로와 forwarded-header 설정.
- Produces: 검증 명령과 운영 URL이 기록된 배포 가능한 main 변경.

- [x] **Step 1: 전체 테스트와 실행 JAR를 검증한다.**

Run: `./gradlew test bootJar --rerun-tasks && git diff --check`

Expected: `BUILD SUCCESSFUL`과 공백 오류 없음.

- [x] **Step 2: 작업 기록을 갱신한다.**

`docs/work-notes/checklist.md`에 Swagger UI 공개, HTTPS forwarded-header 처리, 전체 검증 완료 항목을 기록한다. `docs/work-notes/context-notes.md`에는 Springdoc 3.0.3과 공개 URL `https://safelense.p-e.kr/swagger-ui/index.html`, OpenAPI JSON URL `https://safelense.p-e.kr/v3/api-docs`, TLS 종료가 프록시 책임이라는 결정을 추가한다.

- [ ] **Step 3: 기록을 커밋하고 main에 푸시한다.**

Run: `git add docs/work-notes/checklist.md docs/work-notes/context-notes.md && git commit -m "docs: Swagger 운영 검증 기록" && git push origin main`

Expected: main push가 기존 OIDC SSM 자동 배포를 시작한다.

- [x] **Step 4: 운영 문서 endpoint를 확인한다.**

Run: `curl --fail --location https://safelense.p-e.kr/v3/api-docs && curl --fail --location https://safelense.p-e.kr/swagger-ui/index.html`

Expected: OpenAPI JSON과 Swagger UI HTML이 각각 2xx로 반환된다.

## Self-Review

- Springdoc 의존성, 문서 경로 공개, forwarded header, 기존 JWT·CORS 보존 요구를 Task 1에 모두 포함했다.
- TLS 종료가 프록시 책임이라는 운영 전제와 실제 HTTPS endpoint 검증을 Task 2에 포함했다.
- 구현 시점이 정해지지 않은 항목이나 모호한 구현 지시는 문서에 남기지 않았다.
