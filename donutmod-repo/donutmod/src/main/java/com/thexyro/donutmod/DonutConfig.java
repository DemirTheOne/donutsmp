package com.thexyro.donutmod;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/** config/donutmod/config.json */
public class DonutConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Admin'in Minecraft nicki. UUID ilk girişte otomatik bağlanır. */
    public String adminName = "TheXyro0";
    public String adminUuid = "";

    public String moneyName = "para";
    public String shardName = "shard";

    public long startingMoney = 0;
    public long startingShards = 0;

    public boolean payEnabled = true;
    public boolean giftEnabled = true;

    /** false: sadece oyuncu öldürmeleri kill sayılır. */
    public boolean killsCountMobs = false;

    public int autosaveSeconds = 300;

    public static Path dir() {
        return FabricLoader.getInstance().getConfigDir().resolve("donutmod");
    }

    public static Path file() {
        return dir().resolve("config.json");
    }

    public boolean hasAdmin() {
        return adminUuid != null && !adminUuid.isBlank();
    }

    public boolean isAdmin(UUID id) {
        return hasAdmin() && adminUuid.equalsIgnoreCase(id.toString());
    }

    public static DonutConfig load() {
        Path file = file();
        DonutConfig cfg = null;
        if (Files.exists(file)) {
            try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                cfg = GSON.fromJson(r, DonutConfig.class);
            } catch (Exception e) {
                DonutMod.LOGGER.error("config.json okunamadı, varsayılanlar kullanılıyor (dosyaya dokunulmadı)", e);
                return new DonutConfig();
            }
        }
        if (cfg == null) {
            cfg = new DonutConfig();
        }
        cfg.save(); // eksik alanlar dosyaya yazılsın
        return cfg;
    }

    public void save() {
        try {
            Files.createDirectories(dir());
            Files.writeString(file(), GSON.toJson(this), StandardCharsets.UTF_8);
        } catch (IOException e) {
            DonutMod.LOGGER.error("config.json yazılamadı", e);
        }
    }
}
