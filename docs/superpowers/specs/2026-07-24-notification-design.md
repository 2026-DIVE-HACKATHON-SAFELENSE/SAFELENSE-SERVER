# 알림 기능 설계

## 목표

로그인 사용자가 사진과 같은 알림 화면을 구성할 수 있도록 최신순 목록, 한 건 읽음, 모두 읽음, 전체 미읽음 개수를 제공한다. 노션의 목록·한 건 읽음 명세보다 사진을 우선해 모두 읽음과 `unreadCount`를 추가한다.

## 범위

- `GET /api/v1/notifications`로 커서 기반 목록을 조회한다.
- `PATCH /api/v1/notifications/{notificationId}/read`로 본인 알림 한 건을 읽음 처리한다.
- `PATCH /api/v1/notifications/read-all`로 본인의 모든 미읽음 알림을 처리한다.
- 내부 생성 서비스로 알림을 저장한다.

프론트엔드 화면, 푸시 알림, SSE·WebSocket, 뉴스 수집, 분석 완료 기능과의 실제 연동, 관리자용 생성 API는 포함하지 않는다.

## 데이터 모델

`notifications`는 `id`, `user_id`, `type`, `title`, `body`, 선택적인 `target_type`·`target_id`, `read_at`, `created_at`을 저장한다. 사용자 삭제 시 알림도 삭제한다.

알림 유형은 `ANALYSIS`, `NEWS`, `SYSTEM`이다. 이동 대상 유형은 `ANALYSIS_RESULT`, `NEWS_ARTICLE`이며 이동 대상 유형과 ID는 함께 존재하거나 함께 비어 있어야 한다.

제목은 공백이 아닌 150자 이하, 본문은 공백이 아닌 1,000자 이하로 제한한다. 생성 시각과 읽음 시각은 `Instant`로 다루고 API에서는 UTC ISO-8601 형식으로 반환한다.

## API 계약

### 목록 조회

`GET /api/v1/notifications?cursor=&size=20&unreadOnly=false`는 알림 ID 내림차순으로 조회한다. `cursor`가 있으면 해당 ID보다 작은 알림만 조회하며 `size`는 1부터 100까지 허용한다.

응답은 `notifications`, `unreadCount`, `nextCursor`, `hasNext`를 포함한다. `unreadCount`는 필터와 페이지 위치에 관계없이 사용자의 전체 미읽음 개수다.

각 알림은 `id`, `type`, `title`, `body`, `isRead`, `createdAt`, 선택적인 `targetType`, `targetId`를 반환한다. 아이콘, 색상, 상대 시간과 `오늘·어제·이전` 그룹은 프론트엔드가 계산한다.

### 한 건 읽음

`PATCH /api/v1/notifications/{notificationId}/read`는 본인 소유의 미읽음 알림만 조건부로 변경한다. 이미 읽은 알림은 최초 읽음 시각을 유지하면서 `204 No Content`를 반환한다.

알림이 없거나 다른 사용자 소유이면 모두 `404 NOTIFICATION_NOT_FOUND`를 반환한다.

### 모두 읽음

`PATCH /api/v1/notifications/read-all`은 현재 사용자의 모든 미읽음 알림을 같은 시각으로 일괄 변경한다. 대상이 없어도 `204 No Content`를 반환한다.

## 동시성과 사용자 격리

모든 조회와 변경 조건에 인증 사용자 ID를 포함한다. 읽음 처리는 `read_at IS NULL` 조건부 업데이트를 사용해 동시 재요청이 최초 읽음 시각을 덮어쓰지 않게 한다.

목록과 전체 미읽음 개수는 같은 읽기 전용 트랜잭션에서 조회한다. `(user_id, id)`와 `(user_id, read_at, id)` 인덱스로 최신순 목록과 미읽음 조회를 지원한다.

## 오류 처리

- 유효하지 않은 커서나 크기는 `400 INVALID_REQUEST`다.
- 없는 알림과 다른 사용자 소유 알림은 `404 NOTIFICATION_NOT_FOUND`다.
- 이미 읽은 알림과 비어 있는 모두 읽음 요청은 정상 성공이다.
- 내부 생성 명령의 빈 문자열, 길이 초과, 불완전한 이동 대상 조합은 거절한다.

## 테스트 전략

- 마이그레이션 테스트로 테이블, 외래 키, 삭제 연쇄와 인덱스를 검증한다.
- 서비스 테스트로 생성 검증, 최신순 커서, 미읽음 필터, 전체 미읽음 개수, 읽음 멱등성과 사용자 격리를 검증한다.
- MVC 테스트로 네 HTTP 동작의 응답 구조, 기본값, 요청 경계, 404 오류를 검증한다.
- 알림 집중 테스트, 전체 테스트, 실행 JAR 생성과 공백 검사를 실행한다.

## 완료 기준

- 사진의 목록, 한 건 읽음, 모두 읽음과 읽지 않은 개수를 서버 API로 지원한다.
- 다른 사용자 데이터가 조회되거나 변경되지 않는다.
- 커서 페이지에서 중복과 누락이 없다.
- 중복 읽음 요청이 최초 읽음 시각을 변경하지 않는다.
- 집중 테스트, 전체 테스트와 `bootJar`가 통과한다.
