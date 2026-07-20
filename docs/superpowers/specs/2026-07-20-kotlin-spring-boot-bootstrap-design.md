# Kotlin Spring Boot 기반 설정 설계

## 목적

안심 주택 서비스 서버의 초기 기반으로 Kotlin Spring Boot 웹 애플리케이션과 MySQL/JPA 연결 설정을 제공한다.

## 구성

- Gradle Kotlin DSL, Spring Boot 4.1.0, Kotlin 2.3.10, JVM 24을 사용한다.
- Spring Web, Spring Data JPA, MySQL JDBC 드라이버, Kotlin JSON 지원, 테스트 의존성만 둔다.
- 데이터베이스 접속 정보는 환경 변수로 주입하고 스키마 자동 변경은 하지 않는다.

## 제외 범위

주택 도메인, AI 분석, 레포트, 인증, Docker Compose, API 문서화는 이후 기능 구현 단계에서 다룬다.
