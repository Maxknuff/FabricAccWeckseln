package com.fabricaccweckseln.config;

import com.fabricaccweckseln.MCFabricAccWeckseln;
import com.fabricaccweckseln.auth.MicrosoftAuthService;
import com.fabricaccweckseln.auth.SessionManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.Session;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public class AccountManager {
    private static final AccountManager INSTANCE = new AccountManager();

    private final AccountStorage storage = new AccountStorage();
    private final List<AccountProfile> accounts = new ArrayList<>();
    private AccountProfile activeAccount;

    private AccountManager() {
        loadFromStorage();
    }

    public static AccountManager getInstance() {
        return INSTANCE;
    }

    public List<AccountProfile> getAccounts() {
        return new ArrayList<>(accounts);
    }

    public AccountProfile getActiveAccount() {
        return activeAccount;
    }

    public void loadFromStorage() {
        accounts.clear();
        accounts.addAll(storage.loadAccounts());
        accounts.sort(Comparator.comparingLong(AccountProfile::getLastUsed).reversed());
        if (!accounts.isEmpty()) {
            activeAccount = accounts.get(0);
        }
        refreshActiveAccountAsync();
    }

    public boolean addAccount(AccountProfile account) {
        if (account == null || account.uuid == null || account.uuid.isBlank()) {
            return false;
        }

        for (int index = 0; index < accounts.size(); index++) {
            if (Objects.equals(accounts.get(index).uuid, account.uuid)) {
                accounts.set(index, account);
                persist();
                return true;
            }
        }

        accounts.add(account);
        accounts.sort(Comparator.comparingLong(AccountProfile::getLastUsed).reversed());
        persist();
        return true;
    }

    public void removeAccount(String uuid) {
        if (uuid == null) {
            return;
        }
        accounts.removeIf(account -> Objects.equals(account.uuid, uuid));
        if (activeAccount != null && Objects.equals(activeAccount.uuid, uuid)) {
            activeAccount = accounts.isEmpty() ? null : accounts.get(0);
        }
        persist();
    }

    public void setActiveAccount(AccountProfile account) {
        if (account == null) {
            return;
        }
        AccountProfile resolved = null;
        for (AccountProfile candidate : accounts) {
            if (Objects.equals(candidate.uuid, account.uuid)) {
                resolved = candidate;
                break;
            }
        }
        if (resolved == null) {
            addAccount(account);
            resolved = account;
        }
        resolved.lastUsed = System.currentTimeMillis();
        activeAccount = resolved;
        persist();
        refreshActiveAccountAsync();
    }

    public void persist() {
        storage.saveAccounts(accounts);
    }

    public void refreshActiveAccountAsync() {
        if (activeAccount == null) {
            return;
        }

        Thread thread = new Thread(() -> {
            try {
                MicrosoftAuthService authService = new MicrosoftAuthService();
                AccountProfile refreshed = authService.refreshAccount(activeAccount);
                if (refreshed != null) {
                    Session session = SessionManager.createSession(refreshed.username, refreshed.uuid, refreshed.accessToken);
                    SessionManager.applySession(session);
                    activeAccount = refreshed;
                    persist();
                }
            } catch (Exception ex) {
                MCFabricAccWeckseln.LOGGER.warn("Failed to refresh active account in the background", ex);
            }
        }, "MCFabricAccWeckseln-Refresh");
        thread.setDaemon(true);
        thread.start();
    }

    public void applyAccount(AccountProfile account) {
        if (account == null) {
            return;
        }

        try {
            MicrosoftAuthService authService = new MicrosoftAuthService();
            AccountProfile refreshed = authService.refreshAccount(account);
            Session session = SessionManager.createSession(refreshed.username, refreshed.uuid, refreshed.accessToken);
            SessionManager.applySession(session);
            activeAccount = refreshed;
            account.lastUsed = System.currentTimeMillis();
            persist();
        } catch (Exception ex) {
            MCFabricAccWeckseln.LOGGER.error("Unable to switch account", ex);
        }
    }
}
