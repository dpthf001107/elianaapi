package com.elianayesol.api.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "refresh_tokens")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefreshToken {
	
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	
	@Column(nullable = false, unique = true)
	private String token;
	
	@Column(nullable = false)
	private String userId;
	
	@Column(nullable = false)
	private String provider; // "google", "kakao", "naver"
	
	@Column(nullable = false)
	private LocalDateTime expiresAt;
	
	@Column(nullable = false)
	private LocalDateTime createdAt;
	
	@Column(nullable = false)
	private Boolean isRevoked;
	
	@PrePersist
	protected void onCreate() {
		createdAt = LocalDateTime.now();
		if (isRevoked == null) {
			isRevoked = false;
		}
	}
}

