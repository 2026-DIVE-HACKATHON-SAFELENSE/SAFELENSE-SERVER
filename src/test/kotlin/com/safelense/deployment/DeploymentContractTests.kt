// main 자동 배포와 EC2 Flyway 선행 점검 계약을 검증하는 테스트
package com.safelense.deployment

import java.nio.file.Files
import java.nio.file.Path
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DeploymentContractTests {
    @Test
    fun `main push workflow deploys through OIDC SSM after a Flyway check`() {
        val workflow = Files.readString(Path.of(".github/workflows/deploy.yml"))

        assertThat(workflow).contains("push:")
        assertThat(workflow).contains("branches: [main]")
        assertThat(workflow).contains("contents: read")
        assertThat(workflow).contains("id-token: write")
        assertThat(workflow).contains("vars.AWS_ROLE_TO_ASSUME")
        assertThat(workflow).contains("vars.AWS_REGION")
        assertThat(workflow).contains("vars.EC2_INSTANCE_ID")
        assertThat(workflow).contains("vars.DEPLOY_ARTIFACT_BUCKET")
        assertThat(workflow).contains("releases/safelense-server.jar")
        assertThat(workflow).contains("Run Flyway migration check")
        assertThat(workflow).contains("SPRING_PROFILES_ACTIVE=prod")
        assertThat(workflow).contains("--spring.main.web-application-type=none")
        assertThat(workflow).contains("systemctl is-active --quiet safelense")
        assertThat(workflow).doesNotContain("AWS_ACCESS_KEY_ID")
        assertThat(workflow).doesNotContain("AWS_SECRET_ACCESS_KEY")
        assertThat(workflow).doesNotContain("DB_URL")
        assertThat(workflow).doesNotContain("ssh ")
    }

    @Test
    fun `systemd service starts the application in prod with the staged artifact`() {
        val unit = Files.readString(Path.of("deployment/systemd/safelense.service"))

        assertThat(unit).contains("User=safelense")
        assertThat(unit).contains("Environment=SPRING_PROFILES_ACTIVE=prod")
        assertThat(unit).contains("Environment=AWS_REGION=ap-northeast-2")
        assertThat(unit).contains("ExecStart=/usr/bin/java -jar /opt/safelense/safelense-server.jar")
        assertThat(unit).contains("Restart=on-failure")
    }
}
