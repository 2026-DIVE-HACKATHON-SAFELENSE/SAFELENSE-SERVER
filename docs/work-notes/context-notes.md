# 컨텍스트 노트

- 2026-07-20. 새 저장소에는 프로젝트 파일이 없고 사용자 IDE 파일인 `.idea/`만 존재했다. 해당 파일은 수정하거나 추적하지 않는다.
- 2026-07-20. 모든 애플리케이션 소스는 Kotlin으로 작성한다. 로컬에 설치된 JDK 24에 맞춰 Kotlin JVM 대상도 24로 설정한다.
- 2026-07-20. Web API와 MySQL 영속성 기반만 구성한다. 주택·AI·레포트 기능과 Docker Compose는 범위에서 제외한다.
- 2026-07-20. DB 접속 정보는 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` 환경 변수로만 받으며, Hibernate 자동 DDL은 비활성화한다.
- 2026-07-20. Spring Boot Gradle 플러그인은 BOM을 자동 적용하지 않으므로, Gradle의 기본 BOM 지원으로 `spring-boot-dependencies`를 가져온다.
- 2026-07-20. 진입점 테스트는 먼저 `SafelenseApplication` 부재로 실패한 뒤, 최소 `@SpringBootApplication` Kotlin 클래스를 추가해 통과시킨다.
- 2026-07-20. `./gradlew test`와 `./gradlew bootJar`는 통과했다. MySQL 접속 정보가 없어 실제 DB 연결 기동 검증은 보류한다.
- 2026-07-20. 루트 `application.yml`은 로컬 DB 자격 증명 전용 파일로 사용하고 Git에서 제외한다. `src/main/resources/application.yml`의 공통 설정은 계속 추적한다.
- 2026-07-23. 토큰 재발급은 저장소 없이 서명·만료·`tokenType=refresh` claim을 검증한 뒤 새 액세스 토큰만 발급한다.
- 2026-07-23. 리프레시 토큰은 SHA-256 해시로 MySQL에 저장한다. 사용자당 활성 세션은 하나이며, 갱신마다 새 JWT 쌍으로 교체하고 로그아웃 때 삭제한다.
- 2026-07-23. 로그아웃은 Bearer 액세스 JWT에서 사용자 ID를 검증한 뒤 해당 사용자의 리프레시 토큰 해시를 삭제한다. 액세스 토큰 자체는 만료 전까지 유효하다.
- 2026-07-24. 내 집 정보는 사용자당 현재 한 건만 별도 `home_properties` 테이블에 저장한다.
- 2026-07-24. 최초 등록은 POST, 이후 수정은 `application/merge-patch+json` PATCH로 분리한다. PATCH에서 생략은 유지, 선택 필드의 명시적 null은 삭제로 해석한다.
- 2026-07-24. 내 집 저장 필드는 주소, 만원 단위 보증금, 건물 유형, 선택 임대인명과 계약 예정일이다. 월세, 상세 주소와 변경 이력은 제외한다.
- 2026-07-24. 미등록 조회는 정상 초기 상태로 보고 `200 {"property": null}`을 반환한다. 중복 생성은 409, 미등록 수정은 404로 처리한다.
- 2026-07-24. Spring Boot 4의 HTTP 메시지 변환기와 맞추기 위해 Merge Patch 본문은 Jackson 3의 `tools.jackson.databind.JsonNode`로 파싱한다.
- 2026-07-24. 동시 최초 등록은 DB 유니크 제약을 최종 기준으로 삼고, `saveAndFlush`에서 발생한 제약 위반을 `PROPERTY_ALREADY_EXISTS`로 변환한다.
- 2026-07-24. `./gradlew test --tests 'com.safelense.property.*'`, `./gradlew test`, `./gradlew bootJar`가 모두 통과했다.
