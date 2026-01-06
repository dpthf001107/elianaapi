package com.elianayesol.api.services.oauthservice.kakao;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import com.elianayesol.api.services.oauthservice.jwt.JwtTokenProvider;
import com.elianayesol.api.services.oauthservice.kakao.dto.KakaoTokenResponse;
import com.elianayesol.api.services.oauthservice.kakao.dto.KakaoUserInfo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class KakaoService {
    private final RestTemplate restTemplate;
    private final JwtTokenProvider jwtTokenProvider;
    
    @Value("${kakao.client-id}")
    private String clientId;
    
    @Value("${kakao.client-secret:}")
    private String clientSecret;
    
    @Value("${kakao.redirect-uri}")
    private String redirectUri;
    
    @Value("${kakao.token-uri:https://kauth.kakao.com/oauth/token}")
    private String tokenUri;
    
    @Value("${kakao.user-info-uri:https://kapi.kakao.com/v2/user/me}")
    private String userInfoUri;

    /**
     * Authorization Code로 Access Token 요청
     */
    public KakaoTokenResponse getAccessToken(String code) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "authorization_code");
        params.add("client_id", clientId);
        params.add("redirect_uri", redirectUri);
        params.add("code", code);
        if (clientSecret != null && !clientSecret.isEmpty()) {
            params.add("client_secret", clientSecret);
        }

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

        try {
            if (tokenUri == null || tokenUri.isEmpty()) {
                throw new IllegalStateException("Kakao token URI is not configured");
            }
            ResponseEntity<KakaoTokenResponse> response = restTemplate.exchange(
                    tokenUri,
                    HttpMethod.POST,
                    request,
                    KakaoTokenResponse.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return response.getBody();
            } else {
                log.error("Failed to get access token. Status: {}", response.getStatusCode());
                throw new RuntimeException("Failed to get access token from Kakao");
            }
        } catch (Exception e) {
            log.error("Error getting access token from Kakao", e);
            throw new RuntimeException("Error getting access token from Kakao", e);
        }
    }

    /**
     * Access Token으로 사용자 정보 조회
     */
    public KakaoUserInfo getUserInfo(String accessToken) {
        if (accessToken == null) {
            throw new IllegalArgumentException("Access token cannot be null");
        }

        if (userInfoUri == null || userInfoUri.isEmpty()) {
            throw new IllegalStateException("Kakao user info URI is not configured");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Void> request = new HttpEntity<>(headers);

        try {
            HttpMethod method = Objects.requireNonNull(HttpMethod.GET);
            ResponseEntity<KakaoUserInfo> response = restTemplate.exchange(
                    userInfoUri,
                    method,
                    request,
                    KakaoUserInfo.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return response.getBody();
            } else {
                log.error("Failed to get user info. Status: {}", response.getStatusCode());
                throw new RuntimeException("Failed to get user info from Kakao");
            }
        } catch (Exception e) {
            log.error("Error getting user info from Kakao", e);
            throw new RuntimeException("Error getting user info from Kakao", e);
        }
    }

    /**
     * 카카오 로그인 처리 (메인 로직)
     */
    public OAuthUserResponse processKakaoLogin(String code) {
        // 1. Access Token 획득
        KakaoTokenResponse tokenResponse = getAccessToken(code);

        // 2. 사용자 정보 조회
        KakaoUserInfo userInfo = getUserInfo(tokenResponse.getAccessToken());

        // 3. 카카오 사용자 정보 추출
        String kakaoId = userInfo.getId().toString();
        String email = userInfo.getKakaoAccount() != null
                ? userInfo.getKakaoAccount().getEmail()
                : null;
        String nickname = userInfo.getKakaoAccount() != null
                && userInfo.getKakaoAccount().getProfile() != null
                        ? userInfo.getKakaoAccount().getProfile().getNickname()
                        : null;
        String profileImage = userInfo.getKakaoAccount() != null
                && userInfo.getKakaoAccount().getProfile() != null
                        ? userInfo.getKakaoAccount().getProfile().getProfileImageUrl()
                        : null;

        // 4. JWT 토큰 생성 (카카오 ID를 String으로 사용)
        Map<String, Object> claims = new HashMap<>();
        if (email != null) {
            claims.put("email", email);
        }
        String jwtAccessToken = jwtTokenProvider.generateAccessToken(kakaoId, claims);
        String jwtRefreshToken = jwtTokenProvider.generateRefreshToken(kakaoId);

        // 5. 응답 생성
        return OAuthUserResponse.builder()
                .accessToken(jwtAccessToken)
                .refreshToken(jwtRefreshToken)
                .user(OAuthUserResponse.UserInfo.builder()
                        .id(kakaoId)
                        .email(email)
                        .nickname(nickname)
                        .profileImage(profileImage)
                        .provider("kakao")
                        .build())
                .build();
    }

    /**
     * 카카오 OAuth 응답 DTO
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OAuthUserResponse {
        private String accessToken;
        private String refreshToken;
        private UserInfo user;

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class UserInfo {
            private String id;
            private String email;
            private String nickname;
            private String profileImage;
            private String provider; // "kakao"
        }
    }
}
