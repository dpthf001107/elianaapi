package com.elianayesol.api.oauthservice.google;

import com.elianayesol.api.oauthservice.google.dto.GoogleUserInfo;
import com.elianayesol.api.oauthservice.google.dto.LoginResponse;
import com.elianayesol.api.oauthservice.jwt.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.view.RedirectView;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/oauth/google")
public class GoogleController {

	private final GoogleAuthService googleAuthService;
	private final JwtTokenProvider jwtTokenProvider;

	@Value("${google.frontend-redirect-uri:http://localhost:3000/oauth/google/callback}")
	private String frontendRedirectUri;

	public GoogleController(GoogleAuthService googleAuthService, JwtTokenProvider jwtTokenProvider) {
		this.googleAuthService = googleAuthService;
		this.jwtTokenProvider = jwtTokenProvider;
	}

	/**
	 * 구�? ?�증 URL ?�성
	 */
	@GetMapping("/auth-url")
	public ResponseEntity<Map<String, String>> getGoogleAuthUrl() {
		String authUrl = googleAuthService.generateAuthUrl();
		Map<String, String> response = new HashMap<>();
		response.put("authUrl", authUrl);
		return ResponseEntity.ok(response);
	}

	/**
	 * 구�? 로그??(?��? 코드�?처리)
	 */
	@PostMapping("/login")
	public ResponseEntity<LoginResponse> googleLogin(@RequestBody Map<String, String> body) {
		System.out.println("\n========================================");
		System.out.println("?�� [Google Login] 로그???�청 ?�작");
		System.out.println("========================================");
		
		try {
			// 1. ?��? 코드 �?state 추출
			String code = body.get("code");
			String state = body.get("state");
			
			System.out.println("?�� [Step 1] ?��? 코드 ?�신");
			System.out.println("   - Code: " + (code != null ? code.substring(0, Math.min(20, code.length())) + "..." : "null"));
			System.out.println("   - State: " + (state != null ? state : "null"));
			
			if (code == null || code.isEmpty()) {
				System.out.println("??[Error] ?��? 코드가 ?�습?�다.");
				System.out.println("========================================\n");
				return ResponseEntity.badRequest().body(
						new LoginResponse(false, "?��? 코드가 ?�요?�니??")
				);
			}

			// 2. 구�? ?�세???�큰 ?�청
			System.out.println("\n?�� [Step 2] 구�? ?�세???�큰 ?�청 �?..");
			String googleAccessToken = googleAuthService.getAccessToken(code, state);
			System.out.println("??[Step 2] 구�? ?�세???�큰 ?�득 ?�공");

			// 3. 구�? ?�용???�보 조회
			System.out.println("\n?�� [Step 3] 구�? ?�용???�보 조회 �?..");
			GoogleUserInfo googleUserInfo = googleAuthService.getUserInfo(googleAccessToken);
			System.out.println("??[Step 3] ?�용???�보 조회 ?�공");
			System.out.println("   - Google ID: " + googleUserInfo.getId());
			System.out.println("   - Email: " + googleUserInfo.getEmail());
			System.out.println("   - Name: " + googleUserInfo.getName());

			// 4. JWT ?�큰 ?�성
			System.out.println("\n?�� [Step 4] JWT ?�큰 ?�성 �?..");
			Map<String, Object> claims = new HashMap<>();
			claims.put("googleId", googleUserInfo.getId());
			claims.put("email", googleUserInfo.getEmail());
			claims.put("name", googleUserInfo.getName());
			
			String jwtToken = jwtTokenProvider.generateToken(googleUserInfo.getId(), claims);
			String refreshToken = jwtTokenProvider.generateRefreshToken(googleUserInfo.getId());
			System.out.println("??[Step 4] JWT ?�큰 ?�성 ?�료");
			System.out.println("   - JWT Token: " + jwtToken.substring(0, Math.min(50, jwtToken.length())) + "...");
			System.out.println("   - Refresh Token: " + refreshToken.substring(0, Math.min(50, refreshToken.length())) + "...");

			// 5. ?�용???�보 �??�성
			Map<String, Object> user = new HashMap<>();
			user.put("googleId", googleUserInfo.getId());
			user.put("email", googleUserInfo.getEmail());
			user.put("name", googleUserInfo.getName());
			user.put("givenName", googleUserInfo.getGivenName());
			user.put("familyName", googleUserInfo.getFamilyName());
			user.put("picture", googleUserInfo.getPicture());
			user.put("locale", googleUserInfo.getLocale());

			// 6. ?�답 ?�성
			LoginResponse response = new LoginResponse();
			response.setSuccess(true);
			response.setMessage("구�? 로그???�공");
			response.setToken(jwtToken);
			response.setRefreshToken(refreshToken);
			response.setTokenType("Bearer");
			response.setExpiresIn(86400000L); // 24?�간
			response.setUser(user);
			response.setRedirectUrl(frontendRedirectUri); // 백엔?�에???�정??리디?�션 URL

			System.out.println("\n??[Success] 구�? 로그???�공!");
			System.out.println("   - ?�용?? " + googleUserInfo.getName() + " (" + googleUserInfo.getEmail() + ")");
			System.out.println("   - 리디?�션 URL: " + frontendRedirectUri);
			System.out.println("========================================\n");

			return ResponseEntity.ok(response);

		} catch (Exception e) {
			System.out.println("\n??[Error] 구�? 로그??처리 �??�류 발생");
			System.out.println("   - ?�류 메시지: " + e.getMessage());
			if (e.getCause() != null) {
				System.out.println("   - ?�인: " + e.getCause().getMessage());
			}
			e.printStackTrace();
			System.out.println("========================================\n");
			
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
					new LoginResponse(false, "구�? 로그??처리 �??�류가 발생?�습?�다: " + e.getMessage())
			);
		}
	}

	/**
	 * 구�? 콜백 (GET ?�청) - 브라?��? 리디?�션??
	 * ?�공 ???�론?�엔?�로 리디?�션
	 */
	@GetMapping("/callback")
	public RedirectView googleCallback(
			@RequestParam(required = false) String code,
			@RequestParam(required = false) String state,
			@RequestParam(required = false) String error,
			@RequestHeader(value = "Referer", required = false) String referer) {
		
		System.out.println("\n========================================");
		System.out.println("?�� [Google Callback] 콜백 ?�청 ?�신 (GET)");
		System.out.println("========================================");
		
		// ?�청??Origin???�인?�여 리디?�션 URL 결정
		String baseUrl = determineFrontendUrl(referer);
		String callbackPath = "/oauth/google/callback";
		
		if (error != null) {
			System.out.println("??[Error] 구�? 로그???�패: " + error);
			System.out.println("========================================\n");
			// ?�러 ???�론?�엔??메인 ?�이지�?리디?�션
			return new RedirectView(baseUrl + "/?error=" + URLEncoder.encode(error, StandardCharsets.UTF_8));
		}

		if (code == null || code.isEmpty()) {
			System.out.println("??[Error] ?��? 코드가 ?�습?�다.");
			System.out.println("========================================\n");
			// ?�러 ???�론?�엔??메인 ?�이지�?리디?�션
			return new RedirectView(baseUrl + "/?error=" + URLEncoder.encode("?��? 코드가 ?�요?�니??", StandardCharsets.UTF_8));
		}

		try {
			// 1. 구�? ?�세???�큰 ?�청
			System.out.println("\n?�� [Step 1] 구�? ?�세???�큰 ?�청 �?..");
			String googleAccessToken = googleAuthService.getAccessToken(code, state);
			System.out.println("??[Step 1] 구�? ?�세???�큰 ?�득 ?�공");

			// 2. 구�? ?�용???�보 조회
			System.out.println("\n?�� [Step 2] 구�? ?�용???�보 조회 �?..");
			GoogleUserInfo googleUserInfo = googleAuthService.getUserInfo(googleAccessToken);
			System.out.println("??[Step 2] ?�용???�보 조회 ?�공");
			System.out.println("   - Google ID: " + googleUserInfo.getId());
			System.out.println("   - Email: " + googleUserInfo.getEmail());
			System.out.println("   - Name: " + googleUserInfo.getName());

			// 3. JWT ?�큰 ?�성
			System.out.println("\n?�� [Step 3] JWT ?�큰 ?�성 �?..");
			Map<String, Object> claims = new HashMap<>();
			claims.put("googleId", googleUserInfo.getId());
			claims.put("email", googleUserInfo.getEmail());
			claims.put("name", googleUserInfo.getName());
			
			String jwtToken = jwtTokenProvider.generateToken(googleUserInfo.getId(), claims);
			String refreshToken = jwtTokenProvider.generateRefreshToken(googleUserInfo.getId());
			System.out.println("??[Step 3] JWT ?�큰 ?�성 ?�료");

			// ?�큰??쿼리 ?�라미터�??�달?�여 콜백 ?�이지�?리디?�션
			// 콜백 ?�이지?�서 ?�큰??받아 localStorage???�?�하�??�공 ?�이지 ?�시
			String redirectUrl = baseUrl + callbackPath + 
				"?token=" + URLEncoder.encode(jwtToken, StandardCharsets.UTF_8) +
				"&refreshToken=" + URLEncoder.encode(refreshToken, StandardCharsets.UTF_8) +
				"&success=true";

			System.out.println("\n??[Success] 구�? 로그???�공!");
			System.out.println("   - ?�용?? " + googleUserInfo.getName() + " (" + googleUserInfo.getEmail() + ")");
			System.out.println("   - 리디?�션 URL: " + redirectUrl);
			System.out.println("========================================\n");
			
			return new RedirectView(redirectUrl);
			
		} catch (Exception e) {
			System.out.println("\n??[Error] 구�? 로그??처리 �??�류 발생");
			System.out.println("   - ?�류 메시지: " + e.getMessage());
			if (e.getCause() != null) {
				System.out.println("   - ?�인: " + e.getCause().getMessage());
			}
			e.printStackTrace();
			System.out.println("========================================\n");
			
			// ?�러 ???�론?�엔??메인 ?�이지�?리디?�션
			String errorMessage = URLEncoder.encode("구�? 로그??처리 �??�류가 발생?�습?�다: " + e.getMessage(), StandardCharsets.UTF_8);
			return new RedirectView(baseUrl + "/?error=" + errorMessage);
		}
	}

	/**
	 * Referer ?�더�?기반?�로 ?�론?�엔??URL 결정
	 */
	private String determineFrontendUrl(String referer) {
		if (referer != null) {
			// Referer?�서 ?�트 추출
			if (referer.contains(":3000")) {
				return "http://localhost:3000";
			}
		}
		// 기본값�? www.aifixr.site (?�트 3000)
		return "http://localhost:3000";
	}
}

