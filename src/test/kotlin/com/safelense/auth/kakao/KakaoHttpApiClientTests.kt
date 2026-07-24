// 카카오 토큰 교환과 사용자 정보 조회 HTTP 요청을 검증하는 테스트
package com.safelense.auth.kakao

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.web.client.RestClient

class KakaoHttpApiClientTests {
    private val contextRunner = ApplicationContextRunner()
        .withUserConfiguration(RestClientConfig::class.java)

    @Test
    fun `provides the RestClient builder required by the Kakao API client`() {
        contextRunner.run { context ->
            assertThat(context).hasSingleBean(RestClient.Builder::class.java)
        }
    }

    @Test
    fun `exchanges an authorization code then retrieves a Kakao user`() {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        val client = KakaoHttpApiClient(
            builder,
            KakaoProperties(
                restApiKey = "rest-api-key",
                clientSecret = "client-secret",
                tokenUri = "https://kauth.kakao.com/oauth/token",
                userInfoUri = "https://kapi.kakao.com/v2/user/me",
            ),
        )
        server.expect(requestTo("https://kauth.kakao.com/oauth/token"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("grant_type=authorization_code")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("client_id=rest-api-key")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("code=authorization-code")))
            .andRespond(withSuccess("{\"access_token\":\"kakao-access-token\"}", MediaType.APPLICATION_JSON))
        server.expect(requestTo("https://kapi.kakao.com/v2/user/me"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer kakao-access-token"))
            .andRespond(
                withSuccess(
                    """{"id":123,"kakao_account":{"profile":{"nickname":"라이언","profile_image_url":"https://image.example.com/ryan.png"}}}""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        val user = client.getUser("authorization-code", "https://client.example.com/callback")

        assertThat(user).isEqualTo(KakaoUser(123L, "라이언", "https://image.example.com/ryan.png"))
        server.verify()
    }

    @Test
    fun `converts a Kakao token client error into an authentication exception`() {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        val client = KakaoHttpApiClient(
            builder,
            KakaoProperties("rest-api-key", "client-secret"),
        )
        server.expect(requestTo("https://kauth.kakao.com/oauth/token"))
            .andRespond(withStatus(org.springframework.http.HttpStatus.BAD_REQUEST))

        assertThatThrownBy { client.getUser("authorization-code", "https://client.example.com/callback") }
            .isInstanceOf(KakaoAuthenticationException::class.java)
        server.verify()
    }

    @Test
    fun `uses default Kakao properties when account profile is absent`() {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        val client = KakaoHttpApiClient(builder, KakaoProperties("rest-api-key", "client-secret"))
        server.expect(requestTo("https://kauth.kakao.com/oauth/token"))
            .andRespond(withSuccess("{\"access_token\":\"kakao-access-token\"}", MediaType.APPLICATION_JSON))
        server.expect(requestTo("https://kapi.kakao.com/v2/user/me"))
            .andRespond(
                withSuccess(
                    """{"id":123,"properties":{"nickname":"라이언","profile_image":"https://image.example.com/ryan.png"}}""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        val user = client.getUser("authorization-code", "https://client.example.com/callback")

        assertThat(user).isEqualTo(KakaoUser(123L, "라이언", "https://image.example.com/ryan.png"))
        server.verify()
    }
}
