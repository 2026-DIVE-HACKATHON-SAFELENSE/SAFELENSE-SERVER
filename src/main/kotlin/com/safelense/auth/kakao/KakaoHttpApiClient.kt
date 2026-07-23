// 카카오 토큰과 사용자 정보 REST API를 호출하는 어댑터
package com.safelense.auth.kakao

import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException
import org.springframework.stereotype.Component

private data class KakaoTokenResponse(
    @param:JsonProperty("access_token") val accessToken: String,
)

private data class KakaoProfileResponse(
    val nickname: String?,
    @param:JsonProperty("profile_image_url") val profileImageUrl: String?,
)

private data class KakaoAccountResponse(
    val profile: KakaoProfileResponse?,
)

private data class KakaoPropertiesResponse(
    val nickname: String?,
    @param:JsonProperty("profile_image") val profileImageUrl: String?,
)

private data class KakaoUserResponse(
    val id: Long,
    @param:JsonProperty("kakao_account") val kakaoAccount: KakaoAccountResponse?,
    val properties: KakaoPropertiesResponse?,
)

private data class KakaoProfile(
    val nickname: String?,
    val profileImageUrl: String?,
)

@Component
class KakaoHttpApiClient(
    restClientBuilder: RestClient.Builder,
    private val properties: KakaoProperties,
) : KakaoApiClient {
    private val restClient = restClientBuilder.build()

    override fun getUser(authorizationCode: String, redirectUri: String): KakaoUser {
        val token = requestToken(authorizationCode, redirectUri)
        val kakaoUser = requestUser(token.accessToken)
        val profile = kakaoUser.kakaoAccount?.profile
            ?.let { KakaoProfile(it.nickname, it.profileImageUrl) }
            ?: kakaoUser.properties?.let { KakaoProfile(it.nickname, it.profileImageUrl) }
            ?: throw KakaoApiUnavailableException()
        val nickname = profile.nickname?.takeIf(String::isNotBlank)
            ?: throw KakaoApiUnavailableException()

        return KakaoUser(kakaoUser.id, nickname, profile.profileImageUrl)
    }

    private fun requestToken(authorizationCode: String, redirectUri: String): KakaoTokenResponse =
        callKakao {
            val form = LinkedMultiValueMap<String, String>().apply {
                add("grant_type", "authorization_code")
                add("client_id", properties.restApiKey)
                add("client_secret", properties.clientSecret)
                add("redirect_uri", redirectUri)
                add("code", authorizationCode)
            }

            requireNotNull(
                restClient.post()
                    .uri(properties.tokenUri)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(KakaoTokenResponse::class.java),
            )
        }

    private fun requestUser(accessToken: String): KakaoUserResponse =
        callKakao {
            requireNotNull(
                restClient.get()
                    .uri(properties.userInfoUri)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                    .retrieve()
                    .body(KakaoUserResponse::class.java),
            )
        }

    private fun <T> callKakao(request: () -> T): T = try {
        request()
    } catch (_: HttpClientErrorException) {
        throw KakaoAuthenticationException()
    } catch (_: RestClientResponseException) {
        throw KakaoApiUnavailableException()
    } catch (_: RestClientException) {
        throw KakaoApiUnavailableException()
    }
}
