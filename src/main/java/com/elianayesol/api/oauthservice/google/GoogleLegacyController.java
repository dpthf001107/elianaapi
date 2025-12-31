package com.elianayesol.api.oauthservice.google;

import com.elianayesol.api.oauthservice.google.dto.GoogleUserInfo;
import com.elianayesol.api.oauthservice.jwt.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Google OAuth Legacy Callback Controller
 * Handles the legacy callback path /oauth/google/callback
 * This is needed because Google Cloud Console redirect URI is set to /oauth/google/callback
 */
@RestController
@RequestMapping("/oauth/google")
public class GoogleLegacyController {

	private final GoogleAuthService googleAuthService;
	private final JwtTokenProvider jwtTokenProvider;

	@Value("${google.frontend-redirect-uri:http://localhost:3000/oauth/google/callback}")
	private String frontendRedirectUri;

	public GoogleLegacyController(GoogleAuthService googleAuthService, JwtTokenProvider jwtTokenProvider) {
		this.googleAuthService = googleAuthService;
		this.jwtTokenProvider = jwtTokenProvider;
	}

	/**
	 * Google OAuth Callback (GET request) - Browser redirection
	 * Redirects to frontend on success
	 */
	@GetMapping("/callback")
	public ResponseEntity<Void> googleCallback(
			@RequestParam(required = false) String code,
			@RequestParam(required = false) String state,
			@RequestParam(required = false) String error,
			@RequestHeader(value = "Referer", required = false) String referer) {

		System.out.println("\n========================================");
		System.out.println("🔄 [Google Legacy Callback] Callback request received (GET)");
		System.out.println("========================================");

		String baseUrl = determineFrontendUrl(referer);
		String callbackPath = "/oauth/google/callback";

		if (error != null) {
			System.out.println("❌ [Error] Google login failed: " + error);
			System.out.println("========================================\n");
			String redirectUrl = baseUrl + "/?error=" + URLEncoder.encode(error, StandardCharsets.UTF_8);
			return ResponseEntity.status(HttpStatus.FOUND)
					.header("Location", redirectUrl)
					.build();
		}

		if (code == null || code.isEmpty()) {
			System.out.println("❌ [Error] Authorization code is missing.");
			System.out.println("========================================\n");
			String redirectUrl = baseUrl + "/?error=" + URLEncoder.encode("Authorization code is required.", StandardCharsets.UTF_8);
			return ResponseEntity.status(HttpStatus.FOUND)
					.header("Location", redirectUrl)
					.build();
		}

		try {
			// 1. Get Google access token
			System.out.println("\n🔄 [Step 1] Requesting Google access token...");
			String googleAccessToken = googleAuthService.getAccessToken(code, state);
			System.out.println("✅ [Step 1] Google access token obtained successfully");

			// 2. Get Google user info
			System.out.println("\n🔄 [Step 2] Retrieving Google user info...");
			GoogleUserInfo googleUserInfo = googleAuthService.getUserInfo(googleAccessToken);
			System.out.println("✅ [Step 2] User info retrieved successfully");
			System.out.println("   - Google ID: " + googleUserInfo.getId());
			System.out.println("   - Email: " + googleUserInfo.getEmail());
			System.out.println("   - Name: " + googleUserInfo.getName());

			// 3. Generate JWT token
			System.out.println("\n🔄 [Step 3] Generating JWT token...");
			Map<String, Object> claims = new HashMap<>();
			claims.put("googleId", googleUserInfo.getId());
			claims.put("email", googleUserInfo.getEmail());
			claims.put("name", googleUserInfo.getName());

			String jwtToken = jwtTokenProvider.generateToken(googleUserInfo.getId(), claims);
			String refreshToken = jwtTokenProvider.generateRefreshToken(googleUserInfo.getId());
			System.out.println("✅ [Step 3] JWT token generated successfully");

			// Redirect to frontend with tokens
			String redirectUrl = baseUrl + callbackPath +
					"?token=" + URLEncoder.encode(jwtToken, StandardCharsets.UTF_8) +
					"&refreshToken=" + URLEncoder.encode(refreshToken, StandardCharsets.UTF_8) +
					"&success=true";

			System.out.println("\n✅ [Success] Google login successful (legacy path)!");
			System.out.println("   - User: " + googleUserInfo.getName() + " (" + googleUserInfo.getEmail() + ")");
			System.out.println("   - Redirect URL: " + redirectUrl);
			System.out.println("========================================\n");

			return ResponseEntity.status(HttpStatus.FOUND)
					.header("Location", redirectUrl)
					.build();

		} catch (Exception e) {
			System.out.println("\n❌ [Error] Error occurred during Google login processing (legacy path)");
			System.out.println("   - Error message: " + e.getMessage());
			if (e.getCause() != null) {
				System.out.println("   - Cause: " + e.getCause().getMessage());
			}
			e.printStackTrace();
			System.out.println("========================================\n");

			String errorMessage = URLEncoder.encode("An error occurred during Google login processing: " + e.getMessage(), StandardCharsets.UTF_8);
			String redirectUrl = baseUrl + "/?error=" + errorMessage;
			return ResponseEntity.status(HttpStatus.FOUND)
					.header("Location", redirectUrl)
					.build();
		}
	}

	/**
	 * Determine frontend URL based on Referer header
	 */
	private String determineFrontendUrl(String referer) {
		if (referer != null) {
			if (referer.contains(":3000")) {
				return "http://localhost:3000";
			}
		}
		return "http://localhost:3000";
	}
}

