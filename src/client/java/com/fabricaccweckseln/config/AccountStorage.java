package com.fabricaccweckseln.config;

import com.fabricaccweckseln.MCFabricAccWeckseln;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class AccountStorage {
    private final Path filePath;
    private final Gson gson;

    public AccountStorage() {
        this.filePath = FabricLoader.getInstance().getConfigDir().resolve(MCFabricAccWeckseln.MOD_ID + "_accounts.json");
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    public List<AccountProfile> loadAccounts() {
        try {
            if (!Files.exists(filePath)) {
                return new ArrayList<>();
            }

            String json = Files.readString(filePath);
            if (json == null || json.isBlank()) {
                return new ArrayList<>();
            }

            Type listType = new TypeToken<ArrayList<AccountProfile>>() {}.getType();
            List<AccountProfile> accounts = gson.fromJson(json, listType);
            return accounts == null ? new ArrayList<>() : accounts;
        } catch (Exception ex) {
            MCFabricAccWeckseln.LOGGER.error("Failed to read account storage", ex);
            return new ArrayList<>();
        }
    }

    public void saveAccounts(List<AccountProfile> accounts) {
        try {
            Files.createDirectories(filePath.getParent());
            String json = gson.toJson(accounts, new TypeToken<ArrayList<AccountProfile>>() {}.getType());
            Files.writeString(filePath, json);
        } catch (Exception ex) {
            MCFabricAccWeckseln.LOGGER.error("Failed to save account storage", ex);
        }
    }
}
