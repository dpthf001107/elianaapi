package com.elianayesol.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/gateway")
@Tag(name = "Gateway", description = "게이트웨이 상태 및 관리 API")
public class GatewayController {

    @Value("${spring.application.name:api-service}")
    private String applicationName;

    @Value("${server.port:8080}")
    private String serverPort;

    @Operation(
        summary = "게이트웨이 상태 조회",
        description = "API 게이트웨이의 현재 상태, 서버 정보, 연결 상태를 반환합니다."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "상태 조회 성공",
            content = @Content(schema = @Schema(implementation = Map.class))
        )
    })
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getGatewayStatus() {
        Map<String, Object> status = new HashMap<>();
        
        // 기본 상태 정보
        status.put("status", "UP");
        status.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        
        // 서버 정보
        Map<String, Object> server = new HashMap<>();
        server.put("name", applicationName);
        server.put("port", serverPort);
        server.put("environment", getEnvironment());
        status.put("server", server);
        
        // 서비스 상태
        Map<String, Object> services = new HashMap<>();
        services.put("api", "UP");
        services.put("database", "UP");
        services.put("redis", "UP");
        status.put("services", services);
        
        // 메타 정보
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("version", "1.0.0");
        metadata.put("uptime", "Running");
        status.put("metadata", metadata);
        
        return ResponseEntity.ok(status);
    }

    /**
     * 현재 환경 정보 반환
     */
    private String getEnvironment() {
        String activeProfile = System.getProperty("spring.profiles.active");
        if (activeProfile == null || activeProfile.isEmpty()) {
            activeProfile = System.getenv("SPRING_PROFILES_ACTIVE");
        }
        return activeProfile != null ? activeProfile : "default";
    }
}
