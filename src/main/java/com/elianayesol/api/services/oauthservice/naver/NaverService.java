package com.elianayesol.api.services.oauthservice.naver;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import com.elianayesol.api.services.oauthservice.jwt.JwtTokenProvider;
import com.elianayesol.api.services.oauthservice.naver.dto.NaverTokenResponse;
import com.elianayesol.api.services.oauthservice.naver.dto.NaverUserInfo;

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
public class NaverService {
    private final RestTemplate restTemplate;
    private final JwtTokenProvider jwtTokenProvider;
    
    @Value("${naver.client-id}")
    private String clientId;
    
    @Value("${naver.client-secret}")
    private String clientSecret;
    
    @Value("${naver.redirect-uri}")
    private String redirectUri;
    
    @Value("${naver.token-uri:https://nid.naver.com/oauth2.0/token}")
    private String tokenUri;
    
    @Value("${naver.user-info-uri:https://openapi.naver.com/v1/nid/me}")
    private String userInfoUri;

    /**
     * Authorization Code로 Access Token 요청
     */
    public NaverTokenResponse getAccessToken(String code, String state) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "authorization_code");
        params.add("client_id", clientId);
        params.add("client_secret", clientSecret);
        params.add("redirect_uri", redirectUri);
        params.add("code", code);
        params.add("state", state);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

        try {
            if (tokenUri == null || tokenUri.isEmpty()) {
                throw new IllegalStateException("Naver token URI is not configured");
            }
            ResponseEntity<NaverTokenResponse> response = restTemplate.exchange(
                    tokenUri,
                    HttpMethod.POST,
                    request,
                    NaverTokenResponse.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                NaverTokenResponse body = response.getBody();
                if (body.getError() != null) {
                    log.error("Naver token exchange error: {} - {}", body.getError(), body.getErrorDescription());
                    throw new RuntimeException(
                            "Naver token exchange failed: " + body.getError() + " - " + body.getErrorDescription());
                }
                return body;
            } else {
                log.error("Failed to get access token. Status: {}", response.getStatusCode());
                throw new RuntimeException("Failed to get access token from Naver");
            }
        } catch (Exception e) {
            log.error("Error getting access token from Naver", e);
            throw new RuntimeException("Error getting access token from Naver", e);
        }
    }

    /**
     * Access Token으로 사용자 정보 조회
     */
    public NaverUserInfo getUserInfo(String accessToken) {
        if (accessToken == null) {
            throw new IllegalArgumentException("Access token cannot be null");
        }

        if (userInfoUri == null || userInfoUri.isEmpty()) {
            throw new IllegalStateException("Naver user info URI is not configured");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Void> request = new HttpEntity<>(headers);

        try {
            HttpMethod method = Objects.requireNonNull(HttpMethod.GET);
            ResponseEntity<NaverUserInfo> response = restTemplate.exchange(
                    userInfoUri,
                    method,
                    request,
                    NaverUserInfo.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return response.getBody();
            } else {
                log.error("Failed to get user info. Status: {}", response.getStatusCode());
                throw new RuntimeException("Failed to get user info from Naver");
            }
        } catch (Exception e) {
            log.error("Error getting user info from Naver", e);
            throw new RuntimeException("Error getting user info from Naver", e);
        }
    }

    /**
     * 네이버 로그인 처리 (메인 로직)
     */
    public OAuthUserResponse processNaverLogin(String code, String state) {
        // 1. Access Token 획득
        NaverTokenResponse tokenResponse = getAccessToken(code, state);

        // 2. 사용자 정보 조회
        NaverUserInfo userInfo = getUserInfo(tokenResponse.getAccessToken());

        // 3. 네이버 사용자 정보 추출
        String naverId = userInfo.getId();
        String email = userInfo.getEmail();
        String nickname = userInfo.getNickname();
        String name = userInfo.getName();

        // 4. JWT 토큰 생성
        Map<String, Object> claims = new HashMap<>();
        if (email != null) {
            claims.put("email", email);
        }
        if (name != null) {
            claims.put("name", name);
        }
        String jwtAccessToken = jwtTokenProvider.generateAccessToken(naverId, claims);
        String jwtRefreshToken = jwtTokenProvider.generateRefreshToken(naverId);

        // 5. 응답 생성
        return OAuthUserResponse.builder()
                .accessToken(jwtAccessToken)
                .refreshToken(jwtRefreshToken)
                .user(OAuthUserResponse.UserInfo.builder()
                        .id(naverId)
                        .email(email)
                        .nickname(nickname)
                        .name(name)
                        .provider("naver")
                        .build())
                .build();
    }

    /**
     * 네이버 OAuth 응답 DTO
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
            private String name;
            private String provider; // "naver"
        }
    }
}
