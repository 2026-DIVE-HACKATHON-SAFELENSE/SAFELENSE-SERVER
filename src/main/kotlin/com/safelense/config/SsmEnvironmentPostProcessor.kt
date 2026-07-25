// prod 시작 전에 AWS SSM SecureString을 Spring 환경 속성으로 주입하는 로더
package com.safelense.config

import org.springframework.boot.EnvironmentPostProcessor
import org.springframework.boot.SpringApplication
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.Profiles
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.ssm.SsmClient
import software.amazon.awssdk.services.ssm.model.GetParametersRequest

class SsmEnvironmentPostProcessor(
    private val readerFactory: () -> SsmParameterReader = { AwsSsmParameterReader() },
) : EnvironmentPostProcessor {
    override fun postProcessEnvironment(environment: ConfigurableEnvironment, application: SpringApplication) {
        if (!environment.acceptsProfiles(Profiles.of("prod"))) {
            return
        }

        val reader = readerFactory()
        val values = parameterNames
            .chunked(10)
            .flatMap { reader.read(it).entries }
            .associate { it.toPair() }
        if (values.keys != parameterNames.map { it.substringAfterLast('/') }.toSet()) {
            throw IllegalStateException("Required SSM parameters are unavailable")
        }

        environment.propertySources.addFirst(MapPropertySource("ssmParameters", values))
    }

    companion object {
        val parameterNames = listOf(
            "/safelense/prod/DB_URL",
            "/safelense/prod/DB_USERNAME",
            "/safelense/prod/DB_PASSWORD",
            "/safelense/prod/KAKAO_REST_API_KEY",
            "/safelense/prod/KAKAO_CLIENT_SECRET",
            "/safelense/prod/JWT_SECRET",
            "/safelense/prod/JWT_ACCESS_TOKEN_TTL",
            "/safelense/prod/JWT_REFRESH_TOKEN_TTL",
            "/safelense/prod/OPENAI_API_KEY",
            "/safelense/prod/REGISTRY_DOCUMENT_BUCKET",
            "/safelense/prod/REGISTRY_DOCUMENT_KMS_KEY_ID",
            "/safelense/prod/PUBLIC_DATA_SERVICE_KEY",
            "/safelense/prod/VWORLD_API_KEY",
        )
    }
}

fun interface SsmParameterReader {
    fun read(parameterNames: List<String>): Map<String, String>
}

private class AwsSsmParameterReader : SsmParameterReader {
    override fun read(parameterNames: List<String>): Map<String, String> = try {
        SsmClient.builder()
            .region(Region.AP_NORTHEAST_2)
            .build()
            .use { client ->
                val response = client.getParameters(
                    GetParametersRequest.builder()
                        .names(parameterNames)
                        .withDecryption(true)
                        .build(),
                )
                if (response.invalidParameters().isNotEmpty()) {
                    throw IllegalStateException("Required SSM parameters are unavailable")
                }
                response.parameters().associate { parameter ->
                    parameter.name().substringAfterLast('/') to parameter.value()
                }
            }
    } catch (exception: IllegalStateException) {
        throw exception
    } catch (exception: Exception) {
        throw IllegalStateException("Required SSM parameters are unavailable", exception)
    }
}
