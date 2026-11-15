package com.thexyro.donutmod;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DonutMod implements ModInitializer {
    public static final String MOD_ID = "donutmod";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static DonutConfig config;
    private static DataStore store;
    private static ShopConfig shop;

    private static int tickCounter = 0;
    private static int saveCounter = 0;
    private static int nagCounter = 0;

    public static DonutConfig config() {
        return config;
    }

    public static DataStore store() {
        return store;
    }

    public static ShopConfig shop() {
        return shop;
    }

    public static void reloadConfig() {
        config = DonutConfig.load();
        shop = ShopConfig.load();
    }

    @Override
    public void onInitialize() {
        config = DonutConfig.load();
        shop = ShopConfig.load();
        store = new DataStore(DonutConfig.dir().resolve("players.json"));
        store.load();
        AuthManager.load();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                DonutCommands.register(dispatcher, registryAccess));

        // Oyuncu girişi: veri kaydı aç, admin UUID'sini ilk girişte bağla.
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.getPlayer();
            String name = player.getName().getString();
            store.get(player.getUUID(), name);

            // Tam nick eşleşmesi. Premium'da Mojang doğrular; cracked'de admin komutları
            // ayrıca /register + /login şifresiyle korunur.
            if (!config.hasAdmin() && name.equals(config.adminName)) {
                config.adminUuid = player.getUUID().toString();
                config.save();
                LOGGER.info("Admin UUID'si {} hesabına bağlandı: {}", name, config.adminUuid);
            }

            if (!AuthManager.isVerified(player)) {
                AuthManager.remind(player);
            }
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                AuthManager.endSession(handler.getPlayer().getUUID()));

        // Her saniye oyun süresi say, belirli aralıkla kaydet, giriş yapmayanları uyar.
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (++tickCounter >= 20) {
                tickCounter = 0;
                boolean nag = ++nagCounter >= 30;
                if (nag) {
                    nagCounter = 0;
                }
                for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                    store.addPlaytime(p.getUUID(), p.getName().getString(), 1);
                    if (nag && !AuthManager.isVerified(p)) {
                        AuthManager.remind(p);
                    }
                }
                if (++saveCounter >= Math.max(30, config.autosaveSeconds)) {
                    saveCounter = 0;
                    store.saveIfDirty();
                }
            }
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> store.save());

        // Kill / death sayacı.
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (entity instanceof ServerPlayer victim) {
                store.addDeath(victim.getUUID(), victim.getName().getString());
            }
            Entity attacker = damageSource.getEntity();
            if (attacker instanceof ServerPlayer killer && killer != entity) {
                if (entity instanceof ServerPlayer || config.killsCountMobs) {
                    store.addKill(killer.getUUID(), killer.getName().getString());
                }
            }
        });

        LOGGER.info("DonutMod yüklendi.");
    }
}
