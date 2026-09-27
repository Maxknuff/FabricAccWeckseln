package com.fabricaccweckseln.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;

public final class AccountGuiController {
    private AccountGuiController() {
    }

    public static void openAccountSelection(Screen parent) {
        Minecraft.getInstance().setScreen(new AccountSelectionScreen(parent));
    }

    public static void openAccountSelectionFromTitle() {
        Minecraft.getInstance().setScreen(new AccountSelectionScreen(new TitleScreen()));
    }
}
