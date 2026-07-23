# Home Property API Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 로그인 사용자의 현재 집 정보를 조회·최초 등록·부분 수정하는 API를 제공한다.

**Architecture:** 사용자당 한 행을 보장하는 `home_properties` 테이블과 JPA 엔티티를 추가한다. 서비스는 JWT 사용자 ID에 한정해 조회·생성·부분 수정을 수행하고, 컨트롤러는 Bean Validation과 JSON Merge Patch 파싱을 담당한다.

**Tech Stack:** Kotlin 2.3, Spring Boot 4.1, Spring MVC, Spring Security, Spring Data JPA, Flyway, MySQL, JUnit 5, Mockito.

## Global Constraints

- 보증금은 만원 단위 `Long`으로 저장한다.
- 건물 유형은 `MULTI_FAMILY`, `APARTMENT`, `OFFICETEL`, `DETACHED_HOUSE`만 허용한다.
- PATCH는 `application/merge-patch+json`을 사용한다.
- 모든 새 소스 파일은 첫 줄에 역할을 설명하는 한국어 주석을 둔다.
- 기존 코드와 무관한 리팩터링은 하지 않는다.

---

### Task 1: 작업 문서와 마이그레이션

**Files:**
- Create: `docs/superpowers/specs/2026-07-24-home-property-design.md`
- Create: `src/main/resources/db/migration/V3__create_home_properties.sql`
- Modify: `docs/work-notes/checklist.md`
- Modify: `docs/work-notes/context-notes.md`
- Test: `src/test/kotlin/com/safelense/property/HomePropertyMigrationTests.kt`

**Interfaces:**
- Produces: 사용자당 한 행을 보장하는 `home_properties` 스키마.

- [ ] V3 마이그레이션의 존재와 핵심 제약을 확인하는 실패 테스트를 작성한다.
- [ ] `./gradlew test --tests 'com.safelense.property.HomePropertyMigrationTests'`가 마이그레이션 부재로 실패하는지 확인한다.
- [ ] V3 마이그레이션을 최소 구현한다.
- [ ] 같은 테스트가 통과하는지 확인한다.

### Task 2: 주택 도메인과 서비스

**Files:**
- Create: `src/main/kotlin/com/safelense/property/HomeProperty.kt`
- Create: `src/main/kotlin/com/safelense/property/HomePropertyRepository.kt`
- Create: `src/main/kotlin/com/safelense/property/HomePropertyService.kt`
- Test: `src/test/kotlin/com/safelense/property/HomePropertyServiceTests.kt`

**Interfaces:**
- Produces: `get(userId)`, `create(userId, command)`, `patch(userId, command)` 서비스 계약.

- [ ] 조회·생성·중복·부분 수정·선택 정보 삭제·미등록 수정의 실패 테스트를 작성한다.
- [ ] 관련 테스트가 타입 부재로 실패하는지 확인한다.
- [ ] 엔티티, 저장소, 서비스와 도메인 예외를 최소 구현한다.
- [ ] 서비스 테스트가 통과하는지 확인한다.

### Task 3: HTTP API와 오류 처리

**Files:**
- Create: `src/main/kotlin/com/safelense/property/HomePropertyController.kt`
- Modify: `src/main/kotlin/com/safelense/auth/presentation/ApiExceptionHandler.kt`
- Test: `src/test/kotlin/com/safelense/property/HomePropertyControllerTests.kt`

**Interfaces:**
- Produces: `GET`, `POST`, `PATCH /api/v1/me/property`와 공통 `ApiError` 응답.

- [ ] 성공 응답, principal 전달, POST 검증, Merge Patch 규칙과 오류 상태의 실패 테스트를 작성한다.
- [ ] 관련 테스트가 컨트롤러 부재로 실패하는지 확인한다.
- [ ] 컨트롤러, PATCH 파서와 예외 매핑을 최소 구현한다.
- [ ] 관련 테스트가 통과하는지 확인한다.

### Task 4: 전체 검증과 커밋

- [ ] `./gradlew test --tests 'com.safelense.property.*'`를 실행한다.
- [ ] `./gradlew test`를 실행한다.
- [ ] `./gradlew bootJar`를 실행한다.
- [ ] `git diff --check`와 변경 파일 검토를 수행한다.
- [ ] 문서, 도메인·마이그레이션, HTTP API를 의미 단위로 커밋한다.
