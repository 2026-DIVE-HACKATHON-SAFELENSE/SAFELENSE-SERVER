# CORS 설정 설계

## 목표

브라우저 기반 프론트엔드가 허용된 출처에서 SAFELENSE API를 호출하고, JWT `Authorization` 헤더와 분석 실행의 `Idempotency-Key` 헤더를 포함한 사전 요청이 Spring Security 인증 전에 정상 처리되게 한다.

## 허용 범위

- `http://localhost:8081`
- `https://safelense-fe.pages.dev`
- `https://safelense.site`

허용 메서드는 `GET`, `POST`, `PUT`, `PATCH`, `DELETE`, `OPTIONS`다. 허용 헤더는 `Authorization`, `Content-Type`, `Idempotency-Key`다. JWT는 요청 헤더로 전송하므로 쿠키 기반 자격 증명은 사용하지 않으며 `allowCredentials`를 활성화하지 않는다.

## 설계

`application.yml`의 애플리케이션 설정 아래에 허용 출처 목록을 둔다. `SecurityConfig`는 해당 목록으로 `CorsConfigurationSource` 빈을 만들고 `HttpSecurity.cors`를 활성화한다. Spring Security의 CORS 처리기가 인증 필터보다 먼저 `OPTIONS` 사전 요청에 CORS 응답 헤더를 붙인다.

허용된 출처의 실제 요청은 기존 인증 규칙을 그대로 따른다. 즉, CORS 통과는 인증 우회가 아니며 인증이 필요한 API에는 여전히 유효한 JWT가 필요하다. 목록에 없는 출처에는 `Access-Control-Allow-Origin` 헤더를 보내지 않는다.

## 오류와 제외 범위

와일드카드 출처, 쿠키 자격 증명, 모든 요청 헤더 허용, 배포 도메인 자동 탐색은 제공하지 않는다. 이 작업은 서버 CORS 정책만 변경하며 프론트엔드 배포 설정이나 API 인증 규칙은 변경하지 않는다.

## 검증

Spring Security 통합 테스트로 허용된 출처의 `OPTIONS` 요청이 성공하고 지정된 CORS 헤더를 반환하는지 검증한다. 비허용 출처의 사전 요청에는 허용 출처 헤더가 없는지 검증한다. 기존 인증 엔드포인트의 동작이 유지되는지도 전체 테스트로 확인한다.
