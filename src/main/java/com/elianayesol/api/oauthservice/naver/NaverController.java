package com.elianayesol.api.oauthservice.naver;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;
import com.elianayesol.api.oauthservice.naver.NaverService.OAuthUserResponse;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/oauth/naver")
@RequiredArgsConstructor
@Slf4j
public class NaverController {
    private final NaverService naverService;
    
    @Value("${naver.client-id:}")
    private String clientId;
    
    @Value("${naver.redirect-uri:}")
    private String redirectUri;
    
    @Value("${naver.authorize-uri:https://nid.naver.com/oauth2.0/authorize}")
    private String authorizeUri;
    
    @Value("${naver.frontend-redirect-uri:http://localhost:3000/oauth/naver/callback}")
    private String frontendRedirectUri;

    /**
     * 네이버 로그인 URL 생성
     * GET /naver/login
     */
    @GetMapping("/login")
    public ResponseEntity<Map<String, String>> getNaverLoginUrl() {
        try {
            // Validate configuration
            if (clientId == null || clientId.isEmpty()) {
                log.error("NAVER_CLIENT_ID is not configured");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("error", "Naver OAuth is not properly configured. Please set naver.client-id."));
            }

            if (redirectUri == null || redirectUri.isEmpty()) {
                log.error("NAVER_REDIRECT_URI is not configured");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("error", "Naver redirect URI is not configured."));
            }

            // State 파라미터 생성 (CSRF 방지)
            String state = UUID.randomUUID().toString();

            log.info("Generating Naver login URL with client_id: {}, redirect_uri: {}",
                    clientId.substring(0, Math.min(4, clientId.length())) + "...",
                    redirectUri);

            String loginUrl = UriComponentsBuilder
                    .fromUriString(authorizeUri)
                    .queryParam("response_type", "code")
                    .queryParam("client_id", clientId)
                    .queryParam("redirect_uri", redirectUri)
                    .queryParam("state", state)
                    .build()
                    .toUriString();

            log.info("Generated Naver login URL: {}", loginUrl.replaceAll("client_id=[^&]+", "client_id=***"));
            return ResponseEntity.ok(Map.of("authUrl", loginUrl));
        } catch (Exception e) {
            log.error("Error generating Naver login URL", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to generate login URL: " + e.getMessage()));
        }
    }

    /**
     * 네이버 로그인 콜백 처리
     * GET /naver/callback?code=AUTHORIZATION_CODE&state=STATE
     * 토큰을 생성한 후 프론트엔드로 리다이렉트
     */
    @GetMapping("/callback")
    public ResponseEntity<?> naverCallback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state) {

        // code 파라미터 검증
        if (code == null || code.isEmpty()) {
            log.error("Naver callback: code parameter is missing");
            String redirectUrl = UriComponentsBuilder.fromUriString(frontendRedirectUri)
                    .queryParam("error", URLEncoder.encode("인증 코드가 없습니다.", StandardCharsets.UTF_8))
                    .build()
                    .toUriString();
            return ResponseEntity.status(HttpStatus.FOUND)
                    .header("Location", redirectUrl)
                    .build();
        }

        try {
            log.info("Processing Naver login with code: {}", code.substring(0, Math.min(10, code.length())) + "...");

            OAuthUserResponse response = naverService.processNaverLogin(code, state);
            OAuthUserResponse.UserInfo userInfo = response.getUser();

            if (response == null || response.getAccessToken() == null) {
                throw new RuntimeException("Failed to generate tokens");
            }

            // 프론트엔드로 리다이렉트하면서 토큰과 사용자 정보를 URL 파라미터로 전달
            UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(frontendRedirectUri)
                    .queryParam("accessToken", URLEncoder.encode(response.getAccessToken(), StandardCharsets.UTF_8))
                    .queryParam("refreshToken", URLEncoder.encode(response.getRefreshToken(), StandardCharsets.UTF_8));

            // 사용자 정보를 개별 파라미터로 전달
            if (userInfo != null) {
                if (userInfo.getId() != null) {
                    builder.queryParam("userId", URLEncoder.encode(userInfo.getId(), StandardCharsets.UTF_8));
                }
                if (userInfo.getEmail() != null) {
                    builder.queryParam("email", URLEncoder.encode(userInfo.getEmail(), StandardCharsets.UTF_8));
                }
                if (userInfo.getNickname() != null) {
                    builder.queryParam("nickname", URLEncoder.encode(userInfo.getNickname(), StandardCharsets.UTF_8));
                }
                if (userInfo.getName() != null) {
                    builder.queryParam("name", URLEncoder.encode(userInfo.getName(), StandardCharsets.UTF_8));
                }
                if (userInfo.getProvider() != null) {
                    builder.queryParam("provider", URLEncoder.encode(userInfo.getProvider(), StandardCharsets.UTF_8));
                }
            }

            String redirectUrl = builder.build().toUriString();

            log.info("Successfully processed login, redirecting to frontend");
            return ResponseEntity.status(HttpStatus.FOUND)
                    .header("Location", redirectUrl)
                    .build();
        } catch (Exception e) {
            log.error("Naver login failed with error: {}", e.getMessage(), e);
            // 에러 발생 시 프론트엔드로 리다이렉트하면서 에러 메시지 전달
            String errorMessage = "로그인 처리 중 오류가 발생했습니다: " + e.getMessage();
            if (errorMessage.length() > 200) {
                errorMessage = errorMessage.substring(0, 200);
            }
            String redirectUrl = UriComponentsBuilder.fromUriString(frontendRedirectUri)
                    .queryParam("error", URLEncoder.encode(errorMessage, StandardCharsets.UTF_8))
                    .build()
                    .toUriString();
            log.info("Redirecting to frontend with error: {}", errorMessage);
            return ResponseEntity.status(HttpStatus.FOUND)
                    .header("Location", redirectUrl)
                    .build();
        }
    }
}
