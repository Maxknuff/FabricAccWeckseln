package com.fabricaccweckseln.config;

import java.util.Objects;

public class AccountProfile {
    public String uuid;
    public String username;
    public String refreshToken;
    public long lastUsed;
    public transient String accessToken;

    public AccountProfile() {
    }

    public AccountProfile(String uuid, String username, String refreshToken, long lastUsed) {
        this.uuid = uuid;
        this.username = username;
        this.refreshToken = refreshToken;
        this.lastUsed = lastUsed;
    }

    public String getUuid() {
        return uuid;
    }

    public String getUsername() {
        return username;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public long getLastUsed() {
        return lastUsed;
    }

    public void setLastUsed(long lastUsed) {
        this.lastUsed = lastUsed;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AccountProfile that = (AccountProfile) o;
        return Objects.equals(uuid, that.uuid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uuid);
    }
}
