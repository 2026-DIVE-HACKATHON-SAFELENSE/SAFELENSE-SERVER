// Bearer 액세스 JWT에서 Spring Security 인증 정보를 만드는 필터
package com.safelense.auth.config

import com.safelense.auth.application.InvalidAccessTokenException
import com.safelense.auth.application.JwtTokenIssuer
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class JwtAuthenticationFilter(
    private val jwtTokenIssuer: JwtTokenIssuer,
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val authorization = request.getHeader(HttpHeaders.AUTHORIZATION)
        if (authorization?.startsWith("Bearer ") == true) {
            try {
                val userId = jwtTokenIssuer.validateAccessToken(authorization.removePrefix("Bearer "))
                SecurityContextHolder.getContext().authentication =
                    UsernamePasswordAuthenticationToken(userId, null, emptyList())
            } catch (_: InvalidAccessTokenException) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED)
                return
            }
        }

        filterChain.doFilter(request, response)
    }
}
