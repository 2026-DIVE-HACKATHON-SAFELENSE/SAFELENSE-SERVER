# Notifications Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 사진 기준의 알림 목록, 한 건 읽음, 모두 읽음과 전체 미읽음 개수를 제공한다.

**Architecture:** `com.safelense.notification` 패키지에 알림 영속 모델, 저장소, 서비스와 HTTP 컨트롤러를 둔다. 단일 MySQL 테이블을 기준 데이터로 사용하고 읽음 변경은 조건부 bulk update, 목록은 ID 커서와 별도 미읽음 count 쿼리로 처리한다.

**Tech Stack:** Kotlin 2.3.10, JVM 24, Spring Boot 4.1.0, Spring MVC, Spring Security, Spring Data JPA, Flyway, MySQL, JUnit 5, Mockito.

## Global Constraints

- 사진 기준의 목록, 한 건 읽음, 모두 읽음과 `unreadCount`를 모두 지원한다.
- 알림 유형은 `ANALYSIS`, `NEWS`, `SYSTEM`만 허용한다.
- 이동 대상 유형은 `ANALYSIS_RESULT`, `NEWS_ARTICLE`이며 대상 유형과 ID는 함께 존재하거나 함께 비어야 한다.
- 목록은 ID 내림차순 커서이며 기본 크기는 20, 허용 범위는 1부터 100이다.
- `unreadCount`는 현재 필터와 페이지에 관계없는 사용자 전체 미읽음 개수다.
- 한 건 읽음은 멱등하며 최초 읽음 시각을 덮어쓰지 않는다.
- 모든 조회와 변경은 인증 사용자 ID로 격리한다.
- 새 의존성을 추가하지 않는다.
- 모든 새 Kotlin·SQL 소스 파일 첫 줄에 역할을 설명하는 한국어 주석을 둔다.
- 구현은 테스트를 먼저 실패시킨 뒤 최소 코드로 통과시킨다.
- 관련 없는 코드와 형식을 수정하지 않는다.

---

### Task 1: 알림 마이그레이션

**Files:**
- Create: `src/main/resources/db/migration/V5__create_notifications.sql`
- Test: `src/test/kotlin/com/safelense/notification/NotificationMigrationTests.kt`

**Interfaces:**
- Produces: `notifications` 테이블과 사용자별 최신순·미읽음 인덱스.

- [ ] `NotificationMigrationTests`에 마이그레이션 파일 존재, 컬럼, 사용자 외래 키, `ON DELETE CASCADE`, `(user_id, id)`, `(user_id, read_at, id)` 인덱스 단언을 작성한다.
- [ ] `./gradlew test --tests 'com.safelense.notification.NotificationMigrationTests'`를 실행해 마이그레이션 부재로 실패하는지 확인한다.
- [ ] V5 SQL에 `BIGINT` 기본 키, 사용자 외래 키, enum 문자열·본문·대상·시각 컬럼과 두 인덱스를 최소 구현한다.
- [ ] 같은 집중 테스트를 다시 실행해 통과시킨다.
- [ ] `git add` 후 `feat: 알림 테이블 추가`로 커밋한다.

### Task 2: 내부 알림 생성

**Files:**
- Create: `src/main/kotlin/com/safelense/notification/Notification.kt`
- Create: `src/main/kotlin/com/safelense/notification/NotificationRepository.kt`
- Create: `src/main/kotlin/com/safelense/notification/NotificationService.kt`
- Test: `src/test/kotlin/com/safelense/notification/NotificationServiceTests.kt`

**Interfaces:**
- Produces: `NotificationType`, `NotificationTargetType`, `Notification`, `NotificationCreateCommand`.
- Produces: `NotificationService.create(userId: Long, command: NotificationCreateCommand): Notification`.

- [ ] 서비스 테스트에 정상 생성 시 사용자·유형·정리된 제목과 본문·대상·생성 시각 저장을 작성한다.
- [ ] 빈 제목·본문, 제목 150자 초과, 본문 1,000자 초과, 대상 한쪽만 있는 명령 거절 테스트를 작성한다.
- [ ] 집중 테스트를 실행해 타입 부재로 실패하는지 확인한다.
- [ ] JPA 엔티티, enum, `JpaRepository`, 생성 명령과 최소 생성 서비스를 구현한다.
- [ ] 집중 테스트를 실행해 통과시킨다.
- [ ] `git add` 후 `feat: 내부 알림 생성 기능 추가`로 커밋한다.

### Task 3: 알림 목록과 미읽음 개수

**Files:**
- Modify: `src/main/kotlin/com/safelense/notification/NotificationRepository.kt`
- Modify: `src/main/kotlin/com/safelense/notification/NotificationService.kt`
- Modify: `src/test/kotlin/com/safelense/notification/NotificationServiceTests.kt`

**Interfaces:**
- Produces: `NotificationItem`, `NotificationPage`.
- Produces: `NotificationService.list(userId: Long, cursor: Long?, size: Int, unreadOnly: Boolean): NotificationPage`.

- [ ] 서비스 테스트에 ID 내림차순 결과, `size + 1` 기반 `hasNext`, 마지막 포함 항목 기반 `nextCursor`를 작성한다.
- [ ] 미읽음 필터와 무관한 전체 `unreadCount`, 다음 페이지가 없을 때 `nextCursor = null` 테스트를 작성한다.
- [ ] 잘못된 커서와 1..100 밖 크기 거절 테스트를 작성한다.
- [ ] 집중 테스트를 실행해 새 목록 인터페이스 부재로 실패하는지 확인한다.
- [ ] 저장소 커서 쿼리와 미읽음 count, 읽기 전용 목록 서비스를 최소 구현한다.
- [ ] 집중 테스트를 실행해 통과시킨다.
- [ ] `git add` 후 `feat: 사용자별 알림 목록 조회 추가`로 커밋한다.

### Task 4: 한 건 읽음과 모두 읽음

**Files:**
- Modify: `src/main/kotlin/com/safelense/notification/NotificationRepository.kt`
- Modify: `src/main/kotlin/com/safelense/notification/NotificationService.kt`
- Modify: `src/test/kotlin/com/safelense/notification/NotificationServiceTests.kt`

**Interfaces:**
- Produces: `NotificationService.read(userId: Long, notificationId: Long)`.
- Produces: `NotificationService.readAll(userId: Long)`.
- Produces: `NotificationNotFoundException`.

- [ ] 한 건 조건부 변경 성공과 이미 읽은 본인 알림 재요청 성공 테스트를 작성한다.
- [ ] 없거나 다른 사용자 소유인 경우 `NotificationNotFoundException` 테스트를 작성한다.
- [ ] 모두 읽음이 사용자 범위 bulk update를 한 번 호출하고 대상이 없어도 성공하는 테스트를 작성한다.
- [ ] 집중 테스트를 실행해 읽음 인터페이스 부재로 실패하는지 확인한다.
- [ ] `read_at IS NULL` 조건부 bulk update, 소유 여부 확인과 모두 읽음 update를 최소 구현한다.
- [ ] 집중 테스트를 실행해 통과시킨다.
- [ ] `git add` 후 `feat: 알림 읽음 처리 추가`로 커밋한다.

### Task 5: 알림 HTTP API

**Files:**
- Create: `src/main/kotlin/com/safelense/notification/NotificationController.kt`
- Modify: `src/main/kotlin/com/safelense/auth/presentation/ApiExceptionHandler.kt`
- Test: `src/test/kotlin/com/safelense/notification/NotificationControllerTests.kt`

**Interfaces:**
- Produces: `GET /api/v1/notifications`.
- Produces: `PATCH /api/v1/notifications/{notificationId}/read`.
- Produces: `PATCH /api/v1/notifications/read-all`.

- [ ] MVC 테스트에 목록 기본값, 명시적 커서·크기·필터 전달과 전체 응답 필드를 작성한다.
- [ ] 한 건 읽음과 모두 읽음의 `204` 및 인증 사용자 ID 전달 테스트를 작성한다.
- [ ] 잘못된 커서·크기의 `400 INVALID_REQUEST`와 미소유 알림의 `404 NOTIFICATION_NOT_FOUND` 테스트를 작성한다.
- [ ] 집중 테스트를 실행해 컨트롤러 부재로 실패하는지 확인한다.
- [ ] 컨트롤러, 요청 검증과 예외 매핑을 최소 구현한다.
- [ ] 집중 테스트를 실행해 통과시킨다.
- [ ] `git add` 후 `feat: 알림 조회 및 읽음 API 추가`로 커밋한다.

### Task 6: 전체 검증과 작업 기록

**Files:**
- Modify: `docs/work-notes/checklist.md`
- Modify: `docs/work-notes/context-notes.md`

**Interfaces:**
- Consumes: Tasks 1~5의 모든 알림 기능.

- [ ] `./gradlew test --tests 'com.safelense.notification.*'`를 실행한다.
- [ ] `./gradlew test`를 실행한다.
- [ ] `./gradlew bootJar`를 실행한다.
- [ ] `git diff --check`와 브랜치 전체 diff를 검토한다.
- [ ] 실제 실행 결과와 남은 위험을 체크리스트·컨텍스트 노트에 기록한다.
- [ ] 문서 변경을 `docs: 알림 기능 검증 결과 기록`으로 커밋한다.
