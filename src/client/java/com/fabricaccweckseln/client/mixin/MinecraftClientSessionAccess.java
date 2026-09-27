package com.fabricaccweckseln.client.mixin;

import net.minecraft.client.User;

public interface MinecraftClientSessionAccess {
    User mcfabricaccweckseln$getUser();

    void mcfabricaccweckseln$setUser(User user);
}
