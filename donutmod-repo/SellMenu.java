package com.thexyro.donutmod;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;

/** /sell sandığı: kapatılınca içindekiler satılır, satılamayanlar geri verilir. */
public class SellMenu extends ChestMenu {
    private final SimpleContainer sellBox;

    public SellMenu(int id, Inventory inv, SimpleContainer sellBox) {
        super(MenuType.GENERIC_9x6, id, inv, sellBox, 6);
        this.sellBox = sellBox;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (player instanceof ServerPlayer sp) {
            DonutShop.finishSale(sp, sellBox);
        }
    }
}
