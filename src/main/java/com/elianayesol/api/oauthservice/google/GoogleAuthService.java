package com.elianayesol.api.oauthservice.google;

import com.elianayesol.api.oauthservice.google.dto.GoogleTokenResponse;
import com.elianayesol.api.oauthservice.google.dto.GoogleUserInfo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Service
public class GoogleAuthService {

	private final RestTemplate restTemplate;

	@Value("${google.client-id}")
	private String clientId;

	@Value("${google.client-secret}")
	private String clientSecret;

	@Value("${google.redirect-uri}")
	private String redirectUri;

	private static final String GOOGLE_AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth";
	private static final String GOOGLE_TOKEN_URL = "https://oauth2.googleapis.com/token";
	private static final String GOOGLE_USER_INFO_URL = "https://www.googleapis.com/oauth2/v2/userinfo";

	public GoogleAuthService(RestTemplate restTemplate) {
		this.restTemplate = restTemplate;
	}

	/**
	 * Generate Google Auth URL
	 */
	public String generateAuthUrl() {
		// Generate state parameter (CSRF protection)
		String state = UUID.randomUUID().toString();
		try {
			// URL encoding
			String encodedRedirectUri = URLEncoder.encode(redirectUri, StandardCharsets.UTF_8.toString());
			String encodedScope = URLEncoder.encode("profile email", StandardCharsets.UTF_8.toString());
			String authUrl = GOOGLE_AUTH_URL +
					"?client_id=" + clientId +
					"&redirect_uri=" + encodedRedirectUri +
					"&response_type=code" +
					"&scope=" + encodedScope +
					"&state=" + state +
					"&access_type=offline" +
					"&prompt=consent";
			return authUrl;
		} catch (Exception e) {
			throw new RuntimeException("Failed to generate Google auth URL", e);
		}
	}

	/**
	 * Request Google Access Token
	 */
	public String getAccessToken(String code, String state) {
		System.out.println("   -> Google Token API call: " + GOOGLE_TOKEN_URL);
		
		// Set request headers
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

		// Set request body
		MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
		params.add("grant_type", "authorization_code");
		params.add("client_id", clientId);
		params.add("client_secret", clientSecret);
		params.add("code", code);
		params.add("redirect_uri", redirectUri);

		HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

		try {
			// Call Google Token API
			ResponseEntity<GoogleTokenResponse> response = restTemplate.exchange(
					GOOGLE_TOKEN_URL,
					HttpMethod.POST,
					request,
					GoogleTokenResponse.class
			);

			GoogleTokenResponse tokenResponse = response.getBody();
			if (tokenResponse != null && tokenResponse.getAccessToken() != null) {
				System.out.println("   -> Access token obtained successfully (length: " + tokenResponse.getAccessToken().length() + ")");
				if (tokenResponse.getRefreshToken() != null) {
					System.out.println("   -> Refresh token also obtained");
				}
				return tokenResponse.getAccessToken();
			} else {
				System.out.println("   -> Access token not in response.");
				throw new RuntimeException("Failed to issue Google access token");
			}
		} catch (Exception e) {
			System.out.println("   -> Google access token request failed: " + e.getMessage());
			throw new RuntimeException("Failed to request Google access token", e);
		}
	}

	/**
	 * Get Google User Info
	 */
	public GoogleUserInfo getUserInfo(String accessToken) {
		System.out.println("   -> Google User Info API call: " + GOOGLE_USER_INFO_URL);
		
		// Set request headers
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(accessToken);

		HttpEntity<String> request = new HttpEntity<>(headers);

		try {
			// Call Google User Info API
			ResponseEntity<GoogleUserInfo> response = restTemplate.exchange(
					GOOGLE_USER_INFO_URL,
					HttpMethod.GET,
					request,
					GoogleUserInfo.class
			);

			GoogleUserInfo userInfo = response.getBody();
			if (userInfo != null && userInfo.getId() != null) {
				System.out.println("   -> User info retrieved successfully");
				System.out.println("      - ID: " + userInfo.getId());
				System.out.println("      - Email: " + userInfo.getEmail());
				System.out.println("      - Name: " + userInfo.getName());
				if (userInfo.getPicture() != null) {
					System.out.println("      - Picture: " + userInfo.getPicture());
				}
				return userInfo;
			} else {
				System.out.println("   -> User info not in response.");
				throw new RuntimeException("Failed to retrieve Google user info");
			}
		} catch (Exception e) {
			System.out.println("   -> Google user info retrieval failed: " + e.getMessage());
			throw new RuntimeException("Failed to retrieve Google user info", e);
		}
	}
}
