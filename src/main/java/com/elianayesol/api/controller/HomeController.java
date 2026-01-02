package com.elianayesol.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@Tag(name = "Home", description = "루트 경로 및 기본 정보 API")
public class HomeController {

    @Value("${spring.application.name:api-service}")
    private String applicationName;

    @Value("${server.port:8080}")
    private String serverPort;

    @Operation(
        summary = "API 서버 정보",
        description = "API 서버의 기본 정보와 사용 가능한 엔드포인트를 반환합니다."
    )
    @GetMapping("/")
    public ResponseEntity<Map<String, Object>> home() {
        Map<String, Object> response = new HashMap<>();
        
        response.put("service", applicationName);
        response.put("status", "UP");
        response.put("port", serverPort);
        response.put("message", "API 서버가 정상적으로 실행 중입니다.");
        
        Map<String, String> endpoints = new HashMap<>();
        endpoints.put("health", "/actuator/health");
        endpoints.put("gateway_status", "/api/gateway/status");
        endpoints.put("swagger_ui", "/docs");
        endpoints.put("api_docs", "/v3/api-docs");
        endpoints.put("google_oauth", "/api/oauth/google");
        endpoints.put("kakao_oauth", "/api/oauth/kakao");
        endpoints.put("naver_oauth", "/api/oauth/naver");
        
        response.put("available_endpoints", endpoints);
        
        return ResponseEntity.ok(response);
    }
}

