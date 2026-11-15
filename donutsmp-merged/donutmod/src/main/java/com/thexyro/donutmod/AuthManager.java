package com.thexyro.donutmod;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Cracked (TLauncher) sunucular için basit şifre koruması.
 * Premium (Mojang'ın doğruladığı, UUID sürüm 4) oyuncular otomatik doğrulanmış sayılır.
 * Cracked oyuncular /register ve /login yapana kadar para/eşya komutlarını kullanamaz.
 */
public final class AuthManager {
    private static final int ITERATIONS = 20000;
    public static final int MAX_ATTEMPTS = 5;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type MAP_TYPE = new TypeToken<Map<String, Account>>() {}.getType();

    public static class Account {
        public String salt = "";
        public String hash = "";
    }

    private static final Map<String, Account> accounts = new HashMap<>();
    private static final Set<UUID> sessions = new HashSet<>();
    private static final Map<UUID, Integer> failures = new HashMap<>();

    private AuthManager() {}

    private static Path file() {
        return DonutConfig.dir().resolve("auth.json");
    }

    public static synchronized void load() {
        accounts.clear();
        Path f = file();
        if (!Files.exists(f)) {
            return;
        }
        try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
            Map<String, Account> raw = GSON.fromJson(r, MAP_TYPE);
            if (raw != null) {
                accounts.putAll(raw);
            }
        } catch (Exception e) {
            DonutMod.LOGGER.error("auth.json okunamadı!", e);
        }
    }

    private static void save() {
        try {
            Files.createDirectories(DonutConfig.dir());
            Files.writeString(file(), GSON.toJson(accounts, MAP_TYPE), StandardCharsets.UTF_8);
        } catch (IOException e) {
            DonutMod.LOGGER.error("auth.json yazılamadı", e);
        }
    }

    /** Premium (UUID sürüm 4) ya da bu oturumda şifreyle giriş yapmış. */
    public static synchronized boolean isVerified(ServerPlayer p) {
        return p.getUUID().version() == 4 || sessions.contains(p.getUUID());
    }

    public static synchronized boolean isRegistered(UUID id) {
        return accounts.containsKey(id.toString());
    }

    public static synchronized boolean register(UUID id, String pass) {
        if (isRegistered(id)) {
            return false;
        }
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        Account a = new Account();
        a.salt = Base64.getEncoder().encodeToString(salt);
        a.hash = Base64.getEncoder().encodeToString(hash(pass, salt));
        accounts.put(id.toString(), a);
        save();
        sessions.add(id);
        return true;
    }

    public static synchronized boolean login(UUID id, String pass) {
        Account a = accounts.get(id.toString());
        if (a == null) {
            return false;
        }
        byte[] expected = Base64.getDecoder().decode(a.hash);
        byte[] actual = hash(pass, Base64.getDecoder().decode(a.salt));
        if (MessageDigest.isEqual(expected, actual)) {
            sessions.add(id);
            failures.remove(id);
            return true;
        }
        return false;
    }

    /** Yanlış deneme sayısını artırır ve yeni toplamı döndürür. */
    public static synchronized int fail(UUID id) {
        return failures.merge(id, 1, Integer::sum);
    }

    public static synchronized void endSession(UUID id) {
        sessions.remove(id);
        failures.remove(id);
    }

    /** Doğrulanmamışsa uyarır ve false döner. */
    public static boolean requireAuth(ServerPlayer p) {
        if (isVerified(p)) {
            return true;
        }
        remind(p);
        return false;
    }

    public static void remind(ServerPlayer p) {
        String msg = isRegistered(p.getUUID())
                ? "Giriş yapmadın! Yaz: /login <şifre>"
                : "Kayıt olmadın! Yaz: /register <şifre> <şifre>";
        p.sendSystemMessage(Component.literal(msg).withStyle(ChatFormatting.RED));
    }

    private static byte[] hash(String pass, byte[] salt) {
        try {
            SecretKeyFactory f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            return f.generateSecret(new PBEKeySpec(pass.toCharArray(), salt, ITERATIONS, 256)).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
