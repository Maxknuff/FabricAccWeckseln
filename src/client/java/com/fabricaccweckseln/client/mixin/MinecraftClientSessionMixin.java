package com.fabricaccweckseln.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(Minecraft.class)
public abstract class MinecraftClientSessionMixin implements MinecraftClientSessionAccess {
    @Shadow
    private User user;

    @Override
    public User mcfabricaccweckseln$getUser() {
        return this.user;
    }

    @Override
    public void mcfabricaccweckseln$setUser(User user) {
        this.user = user;
    }
}
