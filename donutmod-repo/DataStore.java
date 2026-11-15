package com.thexyro.donutmod;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Tüm oyuncu verisi. Her değişiklik synchronized metodlardan geçer. */
public class DataStore {
    public static final long MAX = 1_000_000_000_000_000L;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type MAP_TYPE = new TypeToken<Map<String, PlayerData>>() {}.getType();

    private final Path file;
    private final Map<UUID, PlayerData> players = new HashMap<>();
    private boolean dirty = false;

    public DataStore(Path file) {
        this.file = file;
    }

    public synchronized void load() {
        players.clear();
        if (!Files.exists(file)) {
            return;
        }
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Map<String, PlayerData> raw = GSON.fromJson(r, MAP_TYPE);
            if (raw != null) {
                for (Map.Entry<String, PlayerData> e : raw.entrySet()) {
                    try {
                        if (e.getValue() != null) {
                            players.put(UUID.fromString(e.getKey()), e.getValue());
                        }
                    } catch (IllegalArgumentException ex) {
                        DonutMod.LOGGER.warn("players.json içinde geçersiz UUID atlandı: {}", e.getKey());
                    }
                }
            }
        } catch (Exception e) {
            DonutMod.LOGGER.error("players.json okunamadı! Bozuk dosya yedeklenip yeni dosya açılacak.", e);
            try {
                Files.move(file, file.resolveSibling("players.json.bozuk-" + System.currentTimeMillis()));
            } catch (IOException ignored) {
                // yedeklenemediyse de devam
            }
            players.clear();
        }
    }

    public synchronized void save() {
        try {
            Files.createDirectories(file.getParent());
            Map<String, PlayerData> raw = new LinkedHashMap<>();
            for (Map.Entry<UUID, PlayerData> e : players.entrySet()) {
                raw.put(e.getKey().toString(), e.getValue());
            }
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, GSON.toJson(raw, MAP_TYPE), StandardCharsets.UTF_8);
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
            dirty = false;
        } catch (IOException e) {
            DonutMod.LOGGER.error("players.json yazılamadı", e);
        }
    }

    public synchronized void saveIfDirty() {
        if (dirty) {
            save();
        }
    }

    /** Oyuncunun verisini döndürür, yoksa başlangıç değerleriyle oluşturur. */
    public synchronized PlayerData get(UUID id, String name) {
        PlayerData d = players.get(id);
        if (d == null) {
            d = new PlayerData();
            DonutConfig cfg = DonutMod.config();
            d.money = cfg.startingMoney;
            d.shards = cfg.startingShards;
            players.put(id, d);
            dirty = true;
        }
        if (name != null && !name.equals(d.name)) {
            d.name = name;
            dirty = true;
        }
        return d;
    }

    /** Admin düzenlemesi: set / add / remove. Yeni değeri döndürür. */
    public synchronized long modify(UUID id, String name, Stat stat, Op op, long value) {
        PlayerData d = get(id, name);
        value = Math.max(0, Math.min(MAX, value));
        long cur = stat.get(d);
        long next = switch (op) {
            case SET -> value;
            case ADD -> cur + value;
            case REMOVE -> cur - value;
        };
        next = Math.max(0, Math.min(MAX, next));
        stat.set(d, next);
        dirty = true;
        save();
        return next;
    }

    /** Market alışverişi: bakiye yeterliyse düşer. Önce bedel alınır, eşya sonra verilir. */
    public synchronized boolean spend(UUID id, String name, Stat stat, long amount) {
        PlayerData d = get(id, name);
        if (amount < 0 || stat.get(d) < amount) {
            return false;
        }
        stat.set(d, stat.get(d) - amount);
        dirty = true;
        save();
        return true;
    }

    /** Satış geliri vb. */
    public synchronized void earn(UUID id, String name, Stat stat, long amount) {
        PlayerData d = get(id, name);
        stat.set(d, Math.min(MAX, stat.get(d) + Math.max(0, amount)));
        dirty = true;
        save();
    }

    /** /pay: önce göndericiden al, sonra alıcıya ver. */
    public synchronized boolean transfer(UUID from, String fromName, UUID to, String toName, long amount) {
        PlayerData a = get(from, fromName);
        PlayerData b = get(to, toName);
        if (amount <= 0 || a.money < amount || b.money + amount > MAX) {
            return false;
        }
        a.money -= amount; // önce göndericiden al
        b.money += amount; // sonra alıcıya ver
        dirty = true;
        save();
        return true;
    }

    public synchronized void addPlaytime(UUID id, String name, long seconds) {
        get(id, name).playtimeSeconds += seconds;
        dirty = true;
    }

    public synchronized void addKill(UUID id, String name) {
        get(id, name).kills++;
        dirty = true;
    }

    public synchronized void addDeath(UUID id, String name) {
        get(id, name).deaths++;
        dirty = true;
    }
}
