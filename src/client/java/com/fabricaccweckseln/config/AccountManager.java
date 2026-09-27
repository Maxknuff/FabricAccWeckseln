package com.fabricaccweckseln.config;

import com.fabricaccweckseln.MCFabricAccWeckseln;
import com.fabricaccweckseln.auth.MicrosoftAuthService;
import com.fabricaccweckseln.auth.SessionManager;
import net.minecraft.client.User;

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
            refreshActiveAccountAsync();
        }
    }

    public boolean addAccount(AccountProfile account) {
        if (account == null || account.uuid == null || account.uuid.isBlank()) {
            return false;
        }

        for (AccountProfile existing : accounts) {
            if (Objects.equals(existing.uuid, account.uuid)) {
                existing.username = account.username;
                existing.refreshToken = account.refreshToken;
                existing.lastUsed = account.lastUsed;
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
        AccountProfile target = account;
        for (AccountProfile available : accounts) {
            if (Objects.equals(available.uuid, account.uuid)) {
                target = available;
                break;
            }
        }
        target.lastUsed = System.currentTimeMillis();
        activeAccount = target;
        persist();
        switchAccount(target);
    }

    public void persist() {
        storage.saveAccounts(accounts);
    }

    public void refreshActiveAccountAsync() {
        if (activeAccount == null) {
            return;
        }

        Thread worker = new Thread(() -> {
            try {
                MicrosoftAuthService service = new MicrosoftAuthService();
                AccountProfile refreshed = service.refreshAccount(activeAccount);
                if (refreshed != null) {
                    User user = SessionManager.createUser(refreshed.username, refreshed.uuid, refreshed.accessToken);
                    SessionManager.applyUser(user);
                    activeAccount = refreshed;
                    persist();
                }
            } catch (Exception ex) {
                MCFabricAccWeckseln.LOGGER.warn("Failed to refresh account quietly in the background", ex);
            }
        }, "MCFabricAccWeckseln-Refresh");
        worker.setDaemon(true);
        worker.start();
    }

    public void switchAccount(AccountProfile account) {
        if (account == null) {
            return;
        }

        try {
            MicrosoftAuthService service = new MicrosoftAuthService();
            AccountProfile refreshed = service.refreshAccount(account);
            User user = SessionManager.createUser(refreshed.username, refreshed.uuid, refreshed.accessToken);
            SessionManager.applyUser(user);
            activeAccount = refreshed;
            activeAccount.lastUsed = System.currentTimeMillis();
            persist();
        } catch (Exception ex) {
            MCFabricAccWeckseln.LOGGER.error("Unable to switch Minecraft account", ex);
        }
    }
}
