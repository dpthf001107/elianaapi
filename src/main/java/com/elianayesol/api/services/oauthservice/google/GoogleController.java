package com.elianayesol.api.services.oauthservice.google;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.view.RedirectView;

import com.elianayesol.api.services.oauthservice.google.dto.GoogleUserInfo;
import com.elianayesol.api.services.oauthservice.google.dto.LoginResponse;
import com.elianayesol.api.services.oauthservice.jwt.JwtTokenProvider;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/oauth/google")
@Tag(name = "Google OAuth", description = "Google OAuth 인증 API")
public class GoogleController {

	private final GoogleAuthService googleAuthService;
	private final JwtTokenProvider jwtTokenProvider;

	// 프로덕션: FRONTEND_URL=https://www.elianayesol.com (환경 변수)
	// 개발: FRONTEND_URL=http://localhost:3000 (.env 파일 또는 환경 변수)
	// 기본값: 환경 변수가 없으면 프로덕션 도메인 사용
	@Value("${FRONTEND_URL:https://www.elianayesol.com}")
	private String frontendUrl;

	public GoogleController(GoogleAuthService googleAuthService, JwtTokenProvider jwtTokenProvider) {
		this.googleAuthService = googleAuthService;
		this.jwtTokenProvider = jwtTokenProvider;
	}

	/**
	 * Google 인증 URL 생성
	 */
	@Operation(
		summary = "Google OAuth 인증 URL 생성",
		description = "Google OAuth 인증을 위한 URL을 생성하여 반환합니다."
	)
	@ApiResponses(value = {
		@ApiResponse(
			responseCode = "200",
			description = "인증 URL 생성 성공",
			content = @Content(schema = @Schema(implementation = Map.class))
		),
		@ApiResponse(
			responseCode = "500",
			description = "서버 오류"
		)
	})
	@PostMapping("/auth-url")
	public ResponseEntity<Map<String, String>> getGoogleAuthUrl() {
		try {
			String authUrl = googleAuthService.generateAuthUrl();
			Map<String, String> response = new HashMap<>();

			System.out.println("\n========================================");
			System.out.println("🔄 [Google Auth URL] 인증 URL 생성");
			System.out.println("========================================");
			System.out.println("✅ [Success] Google 인증 URL 생성 성공");
			System.out.println("   - Auth URL: " + authUrl);
			System.out.println("========================================\n");

			response.put("authUrl", authUrl);
			return ResponseEntity.ok(response);
		} catch (Exception e) {
			Map<String, String> errorResponse = new HashMap<>();
			errorResponse.put("error", "Google 인증 URL 생성 실패: " + e.getMessage());
			return ResponseEntity.internalServerError().body(errorResponse);
		}
	}

	/**
	 * Google 로그인 (인가 코드로 처리)
	 */
	@PostMapping("/login")
	public ResponseEntity<LoginResponse> googleLogin(@RequestBody Map<String, String> body) {
		System.out.println("\n========================================");
		System.out.println("🔄 [Google Login] 로그인 요청 시작");
		System.out.println("========================================");
		
		try {
			// 1. 인가 코드와 state 추출
			String code = body.get("code");
			String state = body.get("state");
			
			System.out.println("🔄 [Step 1] 인가 코드 수신");
			System.out.println("   - Code: " + (code != null ? code.substring(0, Math.min(20, code.length())) + "..." : "null"));
			System.out.println("   - State: " + (state != null ? state : "null"));
			
			if (code == null || code.isEmpty()) {
				System.out.println("❌[Error] 인가 코드가 없습니다.");
				System.out.println("========================================\n");
				return ResponseEntity.badRequest().body(
						new LoginResponse(false, "인가 코드가 필요합니다")
				);
			}

			// 2. Google 액세스 토큰 요청
			System.out.println("\n🔄 [Step 2] Google 액세스 토큰 요청 중..");
			String googleAccessToken = googleAuthService.getAccessToken(code, state);
			System.out.println("✅ [Step 2] Google 액세스 토큰 획득 성공");

			// 3. Google 사용자 정보 조회
			System.out.println("\n🔄 [Step 3] Google 사용자 정보 조회 중..");
			GoogleUserInfo googleUserInfo = googleAuthService.getUserInfo(googleAccessToken);
			System.out.println("✅ [Step 3] 사용자 정보 조회 성공");
			System.out.println("   - Google ID: " + googleUserInfo.getId());
			System.out.println("   - Email: " + googleUserInfo.getEmail());
			System.out.println("   - Name: " + googleUserInfo.getName());

			// 4. JWT 토큰 생성
			System.out.println("\n🔄 [Step 4] JWT 토큰 생성 중..");
			Map<String, Object> claims = new HashMap<>();
			claims.put("googleId", googleUserInfo.getId());
			claims.put("email", googleUserInfo.getEmail());
			claims.put("name", googleUserInfo.getName());
			
			// Access Token 생성 (짧은 유효기간, 5-15분)
			String jwtToken = jwtTokenProvider.generateAccessToken(googleUserInfo.getId(), claims);
			// Refresh Token 생성 (긴 유효기간, HttpOnly 쿠키에 저장)
			String refreshToken = jwtTokenProvider.generateRefreshToken(googleUserInfo.getId());
			System.out.println("✅ [Step 4] JWT 토큰 생성 완료");
			System.out.println("   - Access Token (전체): " + jwtToken);
			System.out.println("   - Access Token (일부): " + jwtToken.substring(0, Math.min(50, jwtToken.length())) + "...");
			System.out.println("   - Refresh Token (전체): " + refreshToken);
			System.out.println("   - Refresh Token (일부): " + refreshToken.substring(0, Math.min(50, refreshToken.length())) + "...");

			// 5. 사용자 정보 맵 생성
			Map<String, Object> user = new HashMap<>();
			user.put("googleId", googleUserInfo.getId());
			user.put("email", googleUserInfo.getEmail());
			user.put("name", googleUserInfo.getName());
			user.put("givenName", googleUserInfo.getGivenName());
			user.put("familyName", googleUserInfo.getFamilyName());
			user.put("picture", googleUserInfo.getPicture());
			user.put("locale", googleUserInfo.getLocale());

			// 6. 응답 생성
			LoginResponse response = new LoginResponse();
			response.setSuccess(true);
			response.setMessage("Google 로그인 성공");
			response.setToken(jwtToken);
			response.setRefreshToken(refreshToken);
			response.setTokenType("Bearer");
			response.setExpiresIn(86400000L); // 24시간
		response.setUser(user);
		String callbackUrl = frontendUrl + "/oauth/google/callback";
		response.setRedirectUrl(callbackUrl); // 프론트엔드 콜백 URL

		System.out.println("\n✅ [Success] Google 로그인 성공!");
		System.out.println("   - 사용자: " + googleUserInfo.getName() + " (" + googleUserInfo.getEmail() + ")");
		System.out.println("   - 리디렉션 URL: " + callbackUrl);
		System.out.println("========================================\n");

			return ResponseEntity.ok(response);

		} catch (Exception e) {
			System.out.println("\n❌ [Error] Google 로그인 처리 중 오류 발생");
			System.out.println("   - 오류 메시지: " + e.getMessage());
			if (e.getCause() != null) {
				System.out.println("   - 원인: " + e.getCause().getMessage());
			}
			e.printStackTrace();
			System.out.println("========================================\n");
			
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
					new LoginResponse(false, "Google 로그인 처리 중 오류가 발생했습니다: " + e.getMessage())
			);
		}
	}

	/**
	 * Google 콜백 (GET 요청) - 브라우저 리디렉션용
	 * 성공 시 프론트엔드로 리디렉션
	 */
	@GetMapping("/callback")
	public RedirectView googleCallback(
			@RequestParam(required = false) String code,
			@RequestParam(required = false) String state,
			@RequestParam(required = false) String error,
			@RequestHeader(value = "Referer", required = false) String referer) {
		
		System.out.println("\n========================================");
		System.out.println("🔄 [Google Callback] 콜백 요청 수신 (GET)");
		System.out.println("========================================");
		
		// 환경 변수에서 프론트엔드 URL 가져오기
		String baseUrl = frontendUrl;
		String callbackPath = "/oauth/google/callback";
		
		if (error != null) {
			System.out.println("❌❌[Error] Google 로그인 실패: " + error);
			System.out.println("========================================\n");
			// 에러 시 프론트엔드 메인 페이지로 리디렉션
			return new RedirectView(baseUrl + "/?error=" + URLEncoder.encode(error, StandardCharsets.UTF_8));
		}

		if (code == null || code.isEmpty()) {
			System.out.println("❌❌[Error] 인가 코드가 없습니다.");
			System.out.println("========================================\n");
			// 에러 시 프론트엔드 메인 페이지로 리디렉션
			return new RedirectView(baseUrl + "/?error=" + URLEncoder.encode("인가 코드가 필요합니다", StandardCharsets.UTF_8));
		}

		try {
			// 1. Google 액세스 토큰 요청
			System.out.println("\n🔄 [Step 1] Google 액세스 토큰 요청 중..");
			String googleAccessToken = googleAuthService.getAccessToken(code, state);
			System.out.println("✅ [Step 1] Google 액세스 토큰 획득 성공");

			// 2. Google 사용자 정보 조회
			System.out.println("\n🔄 [Step 2] Google 사용자 정보 조회 중..");
			GoogleUserInfo googleUserInfo = googleAuthService.getUserInfo(googleAccessToken);
			System.out.println("✅ [Step 2] 사용자 정보 조회 성공");
			System.out.println("   - Google ID: " + googleUserInfo.getId());
			System.out.println("   - Email: " + googleUserInfo.getEmail());
			System.out.println("   - Name: " + googleUserInfo.getName());

			// 3. JWT 토큰 생성
			System.out.println("\n🔄 [Step 3] JWT 토큰 생성 중..");
			Map<String, Object> claims = new HashMap<>();
			claims.put("googleId", googleUserInfo.getId());
			claims.put("email", googleUserInfo.getEmail());
			claims.put("name", googleUserInfo.getName());
			
			// Access Token 생성 (짧은 유효기간, 5-15분)
			String jwtToken = jwtTokenProvider.generateAccessToken(googleUserInfo.getId(), claims);
			// Refresh Token 생성 (긴 유효기간, HttpOnly 쿠키에 저장)
			String refreshToken = jwtTokenProvider.generateRefreshToken(googleUserInfo.getId());
			System.out.println("✅ [Step 3] JWT 토큰 생성 완료");
			System.out.println("   - Access Token (전체): " + jwtToken);
			System.out.println("   - Access Token (일부): " + jwtToken.substring(0, Math.min(50, jwtToken.length())) + "...");
			System.out.println("   - Refresh Token (전체): " + refreshToken);
			System.out.println("   - Refresh Token (일부): " + refreshToken.substring(0, Math.min(50, refreshToken.length())) + "...");

			// 토큰을 쿼리 파라미터로 전달하여 콜백 페이지로 리디렉션
			// 콜백 페이지에서 토큰을 받아 localStorage에 저장하고 성공 페이지 표시
			String redirectUrl = baseUrl + callbackPath + 
				"?token=" + URLEncoder.encode(jwtToken, StandardCharsets.UTF_8) +
				"&refreshToken=" + URLEncoder.encode(refreshToken, StandardCharsets.UTF_8) +
				"&success=true";

			System.out.println("\n✅ [Success] Google 로그인 성공!");
			System.out.println("   - 사용자: " + googleUserInfo.getName() + " (" + googleUserInfo.getEmail() + ")");
			System.out.println("   - 리디렉션 URL: " + redirectUrl);
			System.out.println("========================================\n");
			
			return new RedirectView(redirectUrl);
			
		} catch (Exception e) {
			System.out.println("\n❌ [Error] Google 로그인 처리 중 오류 발생");
			System.out.println("   - 오류 메시지: " + e.getMessage());
			if (e.getCause() != null) {
				System.out.println("   - 원인: " + e.getCause().getMessage());
			}
			e.printStackTrace();
			System.out.println("========================================\n");
			
			// 에러 시 프론트엔드 메인 페이지로 리디렉션
			String errorMessage = URLEncoder.encode("Google 로그인 처리 중 오류가 발생했습니다: " + e.getMessage(), StandardCharsets.UTF_8);
			return new RedirectView(baseUrl + "/?error=" + errorMessage);
		}
	}

}
