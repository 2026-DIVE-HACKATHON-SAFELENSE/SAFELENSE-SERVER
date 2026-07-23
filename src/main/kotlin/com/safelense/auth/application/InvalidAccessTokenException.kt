// 유효하지 않은 액세스 토큰을 나타내는 인증 예외
package com.safelense.auth.application

class InvalidAccessTokenException : RuntimeException("Access token is invalid.")
