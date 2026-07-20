// Kotlin Spring Boot 애플리케이션 진입점을 검증하는 테스트
package com.safelense

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SafelenseApplicationTests {
    @Test
    fun `Kotlin Spring Boot application class is available`() {
        assertThat(SafelenseApplication::class).isNotNull
    }
}
