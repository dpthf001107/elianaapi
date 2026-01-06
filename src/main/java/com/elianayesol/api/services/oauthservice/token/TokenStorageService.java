package com.elianayesol.api.services.oauthservice.token;

import com.elianayesol.api.entity.RefreshToken;
import com.elianayesol.api.repository.RefreshTokenRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * Token Storage Service
 * - Access Token: Upstash Redis에 저장 (짧은 유효기간, 5-15분)
 * - Refresh Token: Neon DB에 저장 (긴 유효기간, 30일)
 */
@Service
public class TokenStorageService {
	
	private final RedisTemplate<String, String> redisTemplate;
	private final RefreshTokenRepository refreshTokenRepository;
	
	// Access Token 유효기간 (15분)
	private static final long ACCESS_TOKEN_EXPIRATION_MINUTES = 15;
	
	@Autowired
	public TokenStorageService(
			RedisTemplate<String, String> redisTemplate,
			RefreshTokenRepository refreshTokenRepository) {
		this.redisTemplate = redisTemplate;
		this.refreshTokenRepository = refreshTokenRepository;
	}
	
	/**
	 * Access Token을 Upstash Redis에 저장
	 * @param userId 사용자 ID
	 * @param accessToken Access Token
	 */
	public void saveAccessToken(String userId, String accessToken) {
		String key = "access_token:" + userId;
		redisTemplate.opsForValue().set(
			key, 
			accessToken, 
			ACCESS_TOKEN_EXPIRATION_MINUTES, 
			TimeUnit.MINUTES
		);
		System.out.println("✅ [Token Storage] Access Token 저장 완료 (Redis)");
		System.out.println("   - Key: " + key);
		System.out.println("   - Expiration: " + ACCESS_TOKEN_EXPIRATION_MINUTES + " minutes");
	}
	
	/**
	 * Access Token을 Redis에서 조회
	 * @param userId 사용자 ID
	 * @return Access Token 또는 null
	 */
	public String getAccessToken(String userId) {
		String key = "access_token:" + userId;
		return redisTemplate.opsForValue().get(key);
	}
	
	/**
	 * Access Token을 Redis에서 삭제
	 * @param userId 사용자 ID
	 */
	public void deleteAccessToken(String userId) {
		String key = "access_token:" + userId;
		redisTemplate.delete(key);
		System.out.println("✅ [Token Storage] Access Token 삭제 완료 (Redis)");
		System.out.println("   - Key: " + key);
	}
	
	/**
	 * Refresh Token을 Neon DB에 저장
	 * @param userId 사용자 ID
	 * @param refreshToken Refresh Token
	 * @param provider OAuth 제공자 ("google", "kakao", "naver")
	 * @param expiresAt 만료 시간
	 */
	public void saveRefreshToken(String userId, String refreshToken, String provider, LocalDateTime expiresAt) {
		// 기존 토큰이 있으면 삭제
		refreshTokenRepository.findByUserIdAndProvider(userId, provider)
			.ifPresent(existing -> refreshTokenRepository.delete(existing));
		
		// 새 토큰 저장
		RefreshToken tokenEntity = RefreshToken.builder()
			.token(refreshToken)
			.userId(userId)
			.provider(provider)
			.expiresAt(expiresAt)
			.isRevoked(false)
			.build();
		
		refreshTokenRepository.save(tokenEntity);
		System.out.println("✅ [Token Storage] Refresh Token 저장 완료 (Neon DB)");
		System.out.println("   - User ID: " + userId);
		System.out.println("   - Provider: " + provider);
		System.out.println("   - Expires At: " + expiresAt);
	}
	
	/**
	 * Refresh Token을 DB에서 조회
	 * @param token Refresh Token
	 * @return RefreshToken Entity 또는 null
	 */
	public RefreshToken getRefreshToken(String token) {
		return refreshTokenRepository.findByToken(token)
			.filter(t -> !t.getIsRevoked())
			.filter(t -> t.getExpiresAt().isAfter(LocalDateTime.now()))
			.orElse(null);
	}
	
	/**
	 * Refresh Token을 DB에서 삭제 (revoke)
	 * @param token Refresh Token
	 */
	public void revokeRefreshToken(String token) {
		refreshTokenRepository.findByToken(token)
			.ifPresent(tokenEntity -> {
				tokenEntity.setIsRevoked(true);
				refreshTokenRepository.save(tokenEntity);
				System.out.println("✅ [Token Storage] Refresh Token 취소 완료 (Neon DB)");
				System.out.println("   - Token ID: " + tokenEntity.getId());
			});
	}
	
	/**
	 * 사용자의 모든 토큰 삭제
	 * @param userId 사용자 ID
	 * @param provider OAuth 제공자
	 */
	public void deleteAllTokens(String userId, String provider) {
		// Access Token 삭제
		deleteAccessToken(userId);
		
		// Refresh Token 삭제
		refreshTokenRepository.findByUserIdAndProvider(userId, provider)
			.ifPresent(tokenEntity -> {
				tokenEntity.setIsRevoked(true);
				refreshTokenRepository.save(tokenEntity);
			});
		
		System.out.println("✅ [Token Storage] 모든 토큰 삭제 완료");
		System.out.println("   - User ID: " + userId);
		System.out.println("   - Provider: " + provider);
	}
}

