package com.elianayesol.api.services.oauthservice.naver.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class NaverUserInfo {

    @JsonProperty("resultcode")
    private String resultCode;

    @JsonProperty("message")
    private String message;

    @JsonProperty("response")
    private Response response;

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Response {
        @JsonProperty("id")
        private String id;

        @JsonProperty("email")
        private String email;

        @JsonProperty("nickname")
        private String nickname;

        @JsonProperty("name")
        private String name;
    }

    // 편의 메서드 - response 객체에서 직접 가져오기
    public String getId() {
        return response != null ? response.getId() : null;
    }

    public String getEmail() {
        return response != null ? response.getEmail() : null;
    }

    public String getNickname() {
        return response != null ? response.getNickname() : null;
    }

    public String getName() {
        return response != null ? response.getName() : null;
    }
}
