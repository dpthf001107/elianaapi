package com.elianayesol.api.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
	"spring.cloud.config.enabled=false",
	"spring.config.import="
})
class GatewayApplicationTests {

	@Test
	void contextLoads() {
		// Context load test
	}

}
