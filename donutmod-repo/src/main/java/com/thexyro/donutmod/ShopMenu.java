package com.thexyro.donutmod;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.function.Consumer;

/** Sunucu tarafı sandık menüsü: eşya alınamaz, sadece tıklama aksiyonları çalışır. */
public class ShopMenu extends ChestMenu {
    private final Map<Integer, Consumer<ServerPlayer>> actions;
    private final int size;

    private ShopMenu(MenuType<?> type, int id, Inventory inv, SimpleContainer container, int rows,
                     Map<Integer, Consumer<ServerPlayer>> actions) {
        super(type, id, inv, container, rows);
        this.actions = actions;
        this.size = rows * 9;
    }

    public static void open(ServerPlayer player, Component title, int rows, SimpleContainer container,
                            Map<Integer, Consumer<ServerPlayer>> actions) {
        MenuType<?> type = typeFor(rows);
        player.openMenu(new SimpleMenuProvider(
                (id, inv, p) -> new ShopMenu(type, id, inv, container, rows, actions), title));
    }

    static MenuType<?> typeFor(int rows) {
        return switch (rows) {
            case 1 -> MenuType.GENERIC_9x1;
            case 2 -> MenuType.GENERIC_9x2;
            case 3 -> MenuType.GENERIC_9x3;
            case 4 -> MenuType.GENERIC_9x4;
            case 5 -> MenuType.GENERIC_9x5;
            default -> MenuType.GENERIC_9x6;
        };
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        // Hiçbir tıklama envantere işlemez; istemci tahminini sunucu durumuna geri eşitle.
        sendAllDataToRemote();
        if (slotId >= 0 && slotId < size
                && (clickType == ClickType.PICKUP || clickType == ClickType.QUICK_MOVE)
                && player instanceof ServerPlayer sp) {
            Consumer<ServerPlayer> action = actions.get(slotId);
            if (action != null) {
                action.accept(sp);
            }
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}
