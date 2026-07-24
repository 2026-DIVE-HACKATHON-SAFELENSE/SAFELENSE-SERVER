# SAFELENSE EC2 배포

`main`에 push하면 `.github/workflows/deploy.yml`이 OIDC로 AWS 역할을 Assume하고, JAR와 systemd unit을 `releases/` 경로에 업로드한 뒤 SSM Run Command로 EC2에 배포한다.

워크플로는 애플리케이션 비밀값을 읽지 않는다. EC2의 `safelense` 계정이 prod로 실행되며 SSM Parameter Store의 `/safelense/prod/*` SecureString을 시작 전에 읽는다.

배포 명령은 새 JAR를 임시 경로에 내려받은 뒤 `server.port=-1`로 HTTP endpoint를 열지 않으면서 web application context를 한 번 초기화한다. 이 단계에서 Flyway는 migration 이름을 검증하고 현재 Supabase PostgreSQL에 validate-and-migrate를 수행한다. 이 단계가 실패하면 systemd 서비스와 운영 JAR를 바꾸지 않는다.

사전 조건은 다음과 같다.

- EC2는 Amazon Linux 2023용 지원 Java 25를 SSM 명령에서 자동 설치하며, AWS CLI v2가 설치돼 있어야 한다. JAR는 Java 24으로 빌드되지만 Java 25 runtime에서 호환 실행된다.
- GitHub Actions Variables에는 `AWS_ROLE_TO_ASSUME`, `AWS_REGION`, `EC2_INSTANCE_ID`, `DEPLOY_ARTIFACT_BUCKET`만 설정한다.
- PostgreSQL 데이터베이스는 이 저장소의 V1~V7 Flyway migration이 아직 적용되지 않은 새 데이터베이스여야 한다. 기존 MySQL 데이터는 별도 이전 후에 사용한다.
