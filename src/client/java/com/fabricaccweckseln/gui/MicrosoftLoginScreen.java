package com.fabricaccweckseln.gui;

import com.fabricaccweckseln.auth.MicrosoftAuthService;
import com.fabricaccweckseln.config.AccountManager;
import com.fabricaccweckseln.config.AccountProfile;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.awt.Desktop;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.net.URI;

public class MicrosoftLoginScreen extends Screen {
    private final Screen parent;
    private final AccountManager accountManager;
    private final MicrosoftAuthService authService = new MicrosoftAuthService();
    private MicrosoftAuthService.DeviceCodeSession currentSession;
    private String statusText = "Waiting for authorization...";
    private boolean loginFinished;
    private boolean loginFailed;

    public MicrosoftLoginScreen(Screen parent, AccountManager accountManager) {
        super(Component.literal("Microsoft Login"));
        this.parent = parent;
        this.accountManager = accountManager;
    }

    @Override
    protected void init() {
        super.init();
        this.addRenderableWidget(Button.builder(Component.literal("Copy code and open URL"), button -> {
            if (currentSession == null) {
                startLoginFlow();
                return;
            }
            try {
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(currentSession.getUserCode()), null);
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                    Desktop.getDesktop().browse(new URI(currentSession.getVerificationUri()));
                }
                statusText = "Please confirm the login in your browser.";
            } catch (Exception ex) {
                statusText = "Could not copy the code or open the browser.";
                loginFailed = true;
            }
        }).bounds(this.width / 2 - 120, this.height - 56, 240, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Back"), button -> this.minecraft.setScreen(this.parent)).bounds(this.width / 2 - 40, this.height - 28, 80, 20).build());
        startLoginFlow();
    }

    private void startLoginFlow() {
        new Thread(() -> {
            try {
                currentSession = authService.requestDeviceCode();
                statusText = "Code: " + currentSession.getUserCode() + " | URL: " + currentSession.getVerificationUri();
                MicrosoftAuthService.MicrosoftLoginResult result = authService.completeDeviceLogin(currentSession);
                AccountProfile account = result.getAccount();
                accountManager.addAccount(account);
                accountManager.setActiveAccount(account);
                statusText = "Successfully logged in as " + account.username;
                loginFinished = true;
            } catch (Exception ex) {
                statusText = "Login failed: " + ex.getMessage();
                loginFailed = true;
            }
        }, "MCFabricAccWeckseln-DeviceCode").start();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.render(graphics, mouseX, mouseY, partialTicks);
        drawCenteredString(graphics, this.font, this.title, this.width / 2, 30, 0xFFFFFF);
        if (currentSession != null) {
            drawCenteredString(graphics, this.font, "Code: " + currentSession.getUserCode(), this.width / 2, 80, 0xFFFF55);
            drawCenteredString(graphics, this.font, "Open: " + currentSession.getVerificationUri(), this.width / 2, 100, 0xFFFFFF);
        }
        drawCenteredString(graphics, this.font, statusText, this.width / 2, 140, loginFailed ? 0xFF5555 : 0x55FF55);
    }
}
