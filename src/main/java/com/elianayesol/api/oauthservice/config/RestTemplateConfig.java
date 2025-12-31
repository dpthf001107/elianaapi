package com.elianayesol.api.oauthservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * RestTemplate Configuration
 * Used in WebMVC environment
 */
@Configuration
public class RestTemplateConfig {

	/**
	 * RestTemplate Bean
	 */
	@Bean
	public RestTemplate restTemplate() {
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(5000); // 5 seconds
		factory.setReadTimeout(20000); // 20 seconds (sufficient time for Google API calls)
		
		return new RestTemplate(factory);
	}
}
