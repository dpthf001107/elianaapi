package com.elianayesol.api.oauthservice.kakao;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;
import com.elianayesol.api.oauthservice.kakao.KakaoService.OAuthUserResponse;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequestMapping("/api/oauth/kakao")
@RequiredArgsConstructor
@Slf4j
public class KakaoController {
    private final KakaoService kakaoService;
    
    @Value("${kakao.client-id:}")
    private String clientId;
    
    @Value("${kakao.redirect-uri:}")
    private String redirectUri;
    
    @Value("${kakao.authorize-uri:https://kauth.kakao.com/oauth/authorize}")
    private String authorizeUri;
    
    @Value("${kakao.frontend-redirect-uri:http://localhost:3000/oauth/kakao/callback}")
    private String frontendRedirectUri;

    /**
     * 카카오 로그인 URL 생성
     * GET /kakao/login
     */
    @GetMapping("/login")
    public ResponseEntity<Map<String, String>> getKakaoLoginUrl() {
        try {
            // Validate configuration
            if (clientId == null || clientId.isEmpty()) {
                log.error("KAKAO_CLIENT_ID is not configured");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("error", "Kakao OAuth is not properly configured. Please set kakao.client-id."));
            }

            if (redirectUri == null || redirectUri.isEmpty()) {
                log.error("KAKAO_REDIRECT_URI is not configured");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("error", "Kakao redirect URI is not configured."));
            }

            log.info("Generating Kakao login URL with client_id: {}, redirect_uri: {}",
                    clientId.substring(0, Math.min(4, clientId.length())) + "...",
                    redirectUri);

            String loginUrl = UriComponentsBuilder
                    .fromUriString(authorizeUri)
                    .queryParam("client_id", clientId)
                    .queryParam("redirect_uri", redirectUri)
                    .queryParam("response_type", "code")
                    .build()
                    .toUriString();

            log.info("Generated Kakao login URL: {}", loginUrl.replaceAll("client_id=[^&]+", "client_id=***"));
            return ResponseEntity.ok(Map.of("authUrl", loginUrl));
        } catch (Exception e) {
            log.error("Error generating Kakao login URL", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to generate login URL: " + e.getMessage()));
        }
    }

    /**
     * 카카오 로그인 콜백 처리
     * GET /kakao/callback?code=AUTHORIZATION_CODE
     * 토큰을 생성한 후 프론트엔드로 리다이렉트
     */
    @GetMapping("/callback")
    public ResponseEntity<?> kakaoCallback(@RequestParam(required = false) String code) {

        // code 파라미터 검증
        if (code == null || code.isEmpty()) {
            log.error("Kakao callback: code parameter is missing");
            String redirectUrl = UriComponentsBuilder.fromUriString(frontendRedirectUri)
                    .queryParam("error", URLEncoder.encode("인증 코드가 없습니다.", StandardCharsets.UTF_8))
                    .build()
                    .toUriString();
            return ResponseEntity.status(HttpStatus.FOUND)
                    .header("Location", redirectUrl)
                    .build();
        }

        try {
            log.info("Processing Kakao login with code: {}", code.substring(0, Math.min(10, code.length())) + "...");

            OAuthUserResponse response = kakaoService.processKakaoLogin(code);
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
                if (userInfo.getProfileImage() != null) {
                    builder.queryParam("profileImage",
                            URLEncoder.encode(userInfo.getProfileImage(), StandardCharsets.UTF_8));
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
            log.error("Kakao login failed with error: {}", e.getMessage(), e);
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
