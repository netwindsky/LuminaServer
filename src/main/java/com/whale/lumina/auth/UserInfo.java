package com.whale.lumina.auth;

/**
 * 用户信息类
 */
public class UserInfo {
    private final String userId;
    private final String username;
    private final String email;
    private final long registrationTime;
    private final boolean verified;

    public UserInfo(String userId, String username, String email, long registrationTime, boolean verified) {
        this.userId = userId;
        this.username = username;
        this.email = email;
        this.registrationTime = registrationTime;
        this.verified = verified;
    }

    public String getUserId() { return userId; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public long getRegistrationTime() { return registrationTime; }
    public boolean isVerified() { return verified; }
}