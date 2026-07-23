// 카카오 인증 실패와 외부 API 장애를 구분하는 예외 타입
package com.safelense.auth.kakao

class KakaoAuthenticationException : RuntimeException("Kakao authorization failed.")

class KakaoApiUnavailableException : RuntimeException("Kakao API is unavailable.")
