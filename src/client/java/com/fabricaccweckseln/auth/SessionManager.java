package com.fabricaccweckseln.auth;

import com.fabricaccweckseln.client.mixin.MinecraftClientSessionAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;

import java.util.Optional;
import java.util.UUID;

public final class SessionManager {
    private SessionManager() {
    }

    public static User createUser(String username, String uuid, String accessToken) {
        return new User(username, UUID.fromString(uuid), accessToken, Optional.empty(), Optional.empty());
    }

    public static void applyUser(User user) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) {
            ((MinecraftClientSessionAccess) minecraft).mcfabricaccweckseln$setUser(user);
        }
    }

    public static User getCurrentUser() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft != null ? ((MinecraftClientSessionAccess) minecraft).mcfabricaccweckseln$getUser() : null;
    }
}
