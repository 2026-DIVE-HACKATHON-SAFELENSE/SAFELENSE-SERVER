// Swagger UI의 JWT 인증 방식과 SAFELENSE API 기본 정보를 정의하는 설정
package com.safelense.openapi

import io.swagger.v3.oas.annotations.OpenAPIDefinition
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType
import io.swagger.v3.oas.annotations.info.Info
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.security.SecurityScheme
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
@OpenAPIDefinition(
    info = Info(
        title = "SAFELENSE API",
        version = "v1",
        description = "전세 계약 단계별 입력, 위험 분석, 알림과 사용자 정보를 제공하는 API입니다.",
    ),
    security = [SecurityRequirement(name = "bearerAuth")],
)
@SecurityScheme(
    name = "bearerAuth",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT",
    description = "카카오 로그인 또는 토큰 재발급으로 받은 accessToken을 입력합니다.",
)
class OpenApiConfig
