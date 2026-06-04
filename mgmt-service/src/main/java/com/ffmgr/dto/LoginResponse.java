package com.ffmgr.dto;

import java.util.List;

public class LoginResponse {

    private String token;
    private String userId;
    private List<String> roles;

    public LoginResponse() {}

    public LoginResponse(String token, String userId, List<String> roles) {
        this.token = token;
        this.userId = userId;
        this.roles = roles;
    }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public List<String> getRoles() { return roles; }
    public void setRoles(List<String> roles) { this.roles = roles; }
}
