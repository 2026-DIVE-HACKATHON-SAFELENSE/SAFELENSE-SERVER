// Safelense Spring Boot 애플리케이션을 시작하는 진입점
package com.safelense

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class SafelenseApplication

fun main(args: Array<String>) {
    runApplication<SafelenseApplication>(*args)
}
