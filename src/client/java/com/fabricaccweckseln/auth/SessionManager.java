package com.fabricaccweckseln.auth;

import com.fabricaccweckseln.client.mixin.MinecraftClientSessionAccess;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.Session;

import java.lang.reflect.Constructor;
import java.util.Optional;

public final class SessionManager {
    private SessionManager() {
    }

    public static Session createSession(String username, String uuid, String accessToken) {
        try {
            Constructor<Session> constructor = Session.class.getDeclaredConstructor(
                    String.class,
                    String.class,
                    String.class,
                    String.class,
                    String.class,
                    String.class,
                    Session.AccountType.class
            );
            constructor.setAccessible(true);
            return constructor.newInstance(username, uuid, accessToken, "",
                    "", "", Session.AccountType.MSA);
        } catch (Exception ignored) {
            try {
                Constructor<Session> constructor = Session.class.getDeclaredConstructor(
                        String.class,
                        String.class,
                        String.class,
                        String.class
                );
                constructor.setAccessible(true);
                return constructor.newInstance(username, uuid, accessToken, "mojang");
            } catch (Exception ex) {
                try {
                    Constructor<Session> constructor = Session.class.getDeclaredConstructor(
                            String.class,
                            String.class,
                            String.class,
                            String.class,
                            Optional.class,
                            Session.AccountType.class
                    );
                    constructor.setAccessible(true);
                    return constructor.newInstance(username, uuid, accessToken, "", Optional.empty(), Session.AccountType.MSA);
                } catch (Exception fallbackError) {
                    throw new IllegalStateException("Unable to create a valid Session instance for the current Minecraft version.", fallbackError);
                }
            }
        }
    }

    public static void applySession(Session session) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null) {
            ((MinecraftClientSessionAccess) client).mcfabricaccweckseln$setSession(session);
        }
    }
}
