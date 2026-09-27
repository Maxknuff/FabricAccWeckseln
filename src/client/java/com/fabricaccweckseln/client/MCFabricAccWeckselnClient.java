package com.fabricaccweckseln.client;

import com.fabricaccweckseln.MCFabricAccWeckseln;
import com.fabricaccweckseln.config.AccountManager;
import com.fabricaccweckseln.gui.AccountSelectionScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.TitleScreen;
import org.lwjgl.glfw.GLFW;

public class MCFabricAccWeckselnClient implements ClientModInitializer {
    public static final String KEY_BIND_CATEGORY = "MCFabricAccWeckseln";
    public static KeyMapping ACCOUNT_SWITCHER_KEY;

    @Override
    public void onInitializeClient() {
        AccountManager.getInstance();

        ACCOUNT_SWITCHER_KEY = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.mcfabricaccweckseln.accounts",
                GLFW.GLFW_KEY_K,
                KEY_BIND_CATEGORY
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (ACCOUNT_SWITCHER_KEY.consumeClick()) {
                client.setScreen(new AccountSelectionScreen(client.screen != null ? client.screen : new TitleScreen()));
            }
        });

        MCFabricAccWeckseln.LOGGER.info("MCFabricAccWeckseln client initialized");
    }
}