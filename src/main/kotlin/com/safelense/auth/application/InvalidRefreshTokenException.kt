// 유효하지 않은 리프레시 토큰을 나타내는 인증 예외
package com.safelense.auth.application

class InvalidRefreshTokenException : RuntimeException("Refresh token is invalid.")
