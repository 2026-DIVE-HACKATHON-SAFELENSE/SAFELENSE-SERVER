// Safelense Spring Boot 애플리케이션을 시작하는 진입점
package com.safelense

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
class SafelenseApplication

fun main(args: Array<String>) {
    runApplication<SafelenseApplication>(*args)
}
