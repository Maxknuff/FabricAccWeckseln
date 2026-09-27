package com.fabricaccweckseln.gui;

import com.fabricaccweckseln.config.AccountManager;
import com.fabricaccweckseln.config.AccountProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

public class AccountSelectionScreen extends Screen {
    private final Screen parent;
    private final AccountManager accountManager = AccountManager.getInstance();
    private AccountListWidget accountList;

    public AccountSelectionScreen(Screen parent) {
        super(Component.literal("Accounts"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        this.accountList = new AccountListWidget(this.minecraft, this.width - 40, this.height - 120, 32, this.height - 48);
        this.addWidget(this.accountList);

        List<AccountProfile> accounts = accountManager.getAccounts();
        for (AccountProfile account : accounts) {
            this.accountList.addEntry(new AccountListEntry(account));
        }

        this.addRenderableWidget(Button.builder(Component.literal("Select / Login"), button -> {
            AccountListEntry selected = this.accountList.getSelectedEntry();
            if (selected != null) {
                accountManager.switchAccount(selected.account);
                this.minecraft.setScreen(this.parent);
            }
        }).bounds(this.width / 2 - 210, this.height - 28, 120, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Add"), button -> this.minecraft.setScreen(new MicrosoftLoginScreen(this, this.accountManager))).bounds(this.width / 2 - 80, this.height - 28, 80, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Remove"), button -> {
            AccountListEntry selected = this.accountList.getSelectedEntry();
            if (selected != null) {
                accountManager.removeAccount(selected.account.uuid);
                this.accountList.clearEntries();
                for (AccountProfile account : accountManager.getAccounts()) {
                    this.accountList.addEntry(new AccountListEntry(account));
                }
            }
        }).bounds(this.width / 2 + 10, this.height - 28, 80, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Back"), button -> this.minecraft.setScreen(this.parent)).bounds(this.width / 2 + 100, this.height - 28, 90, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.render(graphics, mouseX, mouseY, partialTicks);
        this.accountList.render(graphics, mouseX, mouseY, partialTicks);
        drawCenteredString(graphics, this.font, this.title, this.width / 2, 18, 0xFFFFFF);
    }

    private static class AccountListWidget extends AbstractSelectionList<AccountListEntry> {
        public AccountListWidget(Minecraft client, int width, int height, int top, int bottom) {
            super(client, width, height, top, bottom);
        }

        public AccountListEntry getSelectedEntry() {
            return this.getSelected();
        }
    }

    private class AccountListEntry extends AbstractSelectionList.Entry<AccountListEntry> {
        private final AccountProfile account;

        public AccountListEntry(AccountProfile account) {
            this.account = account;
        }

        @Override
        public void render(GuiGraphics graphics, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float partialTicks) {
            boolean active = accountManager.getActiveAccount() != null && accountManager.getActiveAccount().uuid.equals(this.account.uuid);
            if (active) {
                graphics.fill(x, y, x + entryWidth, y + entryHeight, 0x5522CC88);
            }
            if (this.isSelected()) {
                graphics.fill(x, y, x + entryWidth, y + entryHeight, 0x5522AAFF);
            }
            graphics.drawString(AccountSelectionScreen.this.font, this.account.username, x + 40, y + 9, 0xFFFFFF, false);
            if (active) {
                graphics.drawString(AccountSelectionScreen.this.font, "Active", x + entryWidth - 75, y + 9, 0x55FF55, false);
            }
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button == 0) {
                AccountSelectionScreen.this.accountList.setSelected(this);
                return true;
            }
            return false;
        }
    }
}
