# 공개 Swagger와 HTTPS API 문서 설계

## 목표

운영 API 주소 `https://safelense.p-e.kr`에서 OpenAPI 문서와 Swagger UI를 공개한다. Swagger UI의 Try it out 요청은 프록시가 전달한 HTTPS 정보를 사용한다. 기존 프런트 출처의 CORS 허용 정책은 유지한다.

## 구성

- Spring Boot 4와 호환되는 `springdoc-openapi-starter-webmvc-ui` 3.0.3을 추가한다.
- springdoc 기본 경로인 `/v3/api-docs`와 `/swagger-ui/index.html`을 사용한다.
- Spring Security는 `/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html`만 인증 없이 허용한다. 업무 API의 기존 인증 정책은 바꾸지 않는다.
- `server.forward-headers-strategy=framework`를 설정해 Nginx 또는 로드 밸런서의 `X-Forwarded-Proto`와 Host 헤더를 처리한다. 따라서 운영 Swagger UI는 HTTPS URL로 요청하고, 로컬에서는 로컬 요청 주소를 그대로 사용한다.

## HTTPS 전제

TLS 인증서와 `https://safelense.p-e.kr`의 프록시 연결은 인프라가 제공한다. 애플리케이션은 HTTP로 실행되더라도 forwarded header를 통해 외부 HTTPS 주소를 인식한다.

## 검증

- 의존성, forwarded header 설정, 공개 문서 경로의 Security 허용을 정적 계약 테스트로 검증한다.
- 기존 CORS 테스트와 전체 Gradle 테스트 및 `bootJar`를 실행한다.
- main push 자동 배포 뒤 `https://safelense.p-e.kr/v3/api-docs`와 Swagger UI를 확인한다.
