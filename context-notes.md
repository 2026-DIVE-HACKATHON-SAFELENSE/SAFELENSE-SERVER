# 컨텍스트 노트

- 2026-07-20. 새 저장소에는 프로젝트 파일이 없고 사용자 IDE 파일인 `.idea/`만 존재했다. 해당 파일은 수정하거나 추적하지 않는다.
- 2026-07-20. 모든 애플리케이션 소스는 Kotlin으로 작성한다. 로컬에 설치된 JDK 24에 맞춰 Kotlin JVM 대상도 24로 설정한다.
- 2026-07-20. Web API와 MySQL 영속성 기반만 구성한다. 주택·AI·레포트 기능과 Docker Compose는 범위에서 제외한다.
- 2026-07-20. DB 접속 정보는 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` 환경 변수로만 받으며, Hibernate 자동 DDL은 비활성화한다.
- 2026-07-20. Spring Boot Gradle 플러그인은 BOM을 자동 적용하지 않으므로, Gradle의 기본 BOM 지원으로 `spring-boot-dependencies`를 가져온다.
- 2026-07-20. 진입점 테스트는 먼저 `SafelenseApplication` 부재로 실패한 뒤, 최소 `@SpringBootApplication` Kotlin 클래스를 추가해 통과시킨다.
- 2026-07-20. `./gradlew test`와 `./gradlew bootJar`는 통과했다. MySQL 접속 정보가 없어 실제 DB 연결 기동 검증은 보류한다.
