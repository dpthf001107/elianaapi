package com.elianayesol.api.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

	@Override
	public void addCorsMappings(CorsRegistry registry) {
		registry.addMapping("/**")
				// Spring Boot 3.x에서는 allowedOriginPatterns 사용 권장
				.allowedOriginPatterns(
					"http://localhost:3000", 
					"http://127.0.0.1:3000",
					"https://www.elianayesol.com",  // 프로덕션 도메인
					"https://elianayesol.com"       // www 없는 도메인도 허용
				)
				.allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
				.allowedHeaders("*")
				.allowCredentials(true)
				.maxAge(3600);
	}
}

