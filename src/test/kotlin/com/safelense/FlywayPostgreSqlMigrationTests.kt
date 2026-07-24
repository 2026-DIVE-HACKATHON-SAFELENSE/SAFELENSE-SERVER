// 실제 PostgreSQL에서 Flyway 전체 마이그레이션을 검증하는 컨테이너 테스트
package com.safelense

import org.assertj.core.api.Assertions.assertThat
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Test
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer

@Testcontainers(disabledWithoutDocker = true)
class FlywayPostgreSqlMigrationTests {
    @Test
    fun `fresh PostgreSQL applies every migration in order`() {
        val flyway = Flyway.configure()
            .dataSource(postgresql.jdbcUrl, postgresql.username, postgresql.password)
            .locations("classpath:db/migration")
            .load()

        flyway.migrate()

        assertThat(flyway.info().applied().map { it.version.version })
            .containsExactly("1", "2", "3", "4", "5", "6", "7")
        postgresql.createConnection("").use { connection ->
            connection.prepareStatement(
                """
                SELECT data_type
                FROM information_schema.columns
                WHERE table_name = 'analysis_documents' AND column_name = 'content'
                """.trimIndent(),
            ).use { statement ->
                statement.executeQuery().use { result ->
                    assertThat(result.next()).isTrue()
                    assertThat(result.getString("data_type")).isEqualTo("bytea")
                }
            }
        }
    }

    companion object {
        @Container
        @JvmStatic
        val postgresql = PostgreSQLContainer("postgres:17-alpine")
    }
}
