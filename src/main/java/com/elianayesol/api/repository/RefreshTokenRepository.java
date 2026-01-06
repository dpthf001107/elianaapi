package com.elianayesol.api.repository;

import com.elianayesol.api.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
	
	Optional<RefreshToken> findByToken(String token);
	
	Optional<RefreshToken> findByUserIdAndProvider(String userId, String provider);
	
	void deleteByToken(String token);
	
	void deleteByUserIdAndProvider(String userId, String provider);
}

