package com.thexyro.donutmod;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.Enchantment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Market mantığı: menüler, satın alma, /sell. Tüm içerik shop.json'dan gelir. */
public final class DonutShop {
    private static Map<String, Item> itemIndex;

    private DonutShop() {}

    // ------------------------------------------------------------ menüler

    public static void openMain(ServerPlayer player) {
        if (!AuthManager.requireAuth(player)) {
            return;
        }
        ShopConfig sc = DonutMod.shop();
        SimpleContainer c = new SimpleContainer(27);
        fill(c);
        Map<Integer, Consumer<ServerPlayer>> actions = new HashMap<>();

        int slot = 10;
        for (ShopConfig.Section s : sc.sections) {
            if (slot > 16) {
                break;
            }
            ItemStack icon = icon(s.icon, nz(s.title), ChatFormatting.GOLD);
            List<Component> lore = new ArrayList<>();
            lore.add(plain("Para birimi: " + currencyName(s)).withStyle(ChatFormatting.GRAY));
            icon.set(DataComponents.LORE, new ItemLore(lore));
            c.setItem(slot, icon);
            actions.put(slot, p -> openSection(p, s));
            slot++;
        }

        if (sc.sellEnabled) {
            ItemStack sell = icon("minecraft:hopper", "Sat", ChatFormatting.GREEN);
            c.setItem(22, sell);
            actions.put(22, DonutShop::openSell);
        }

        ShopMenu.open(player, plain("Market"), 3, c, actions);
    }

    static void openSection(ServerPlayer player, ShopConfig.Section s) {
        int rows = Math.max(1, Math.min(6, s.rows));
        SimpleContainer c = new SimpleContainer(rows * 9);
        fill(c);
        Map<Integer, Consumer<ServerPlayer>> actions = new HashMap<>();

        for (ShopConfig.Entry e : s.items) {
            if (e.slot < 0 || e.slot >= rows * 9) {
                continue;
            }
            ItemStack shown = display(player, s, e);
            if (shown == null) {
                continue;
            }
            c.setItem(e.slot, shown);
            actions.put(e.slot, p -> buy(p, s, e));
        }

        int back = rows * 9 - 5;
        c.setItem(back, button(Items.ARROW, "Geri", ChatFormatting.RED));
        actions.put(back, DonutShop::openMain);

        ShopMenu.open(player, plain(nz(s.title)), rows, c, actions);
    }

    // ---------------------------------------------------------- satın alma

    static void buy(ServerPlayer player, ShopConfig.Section s, ShopConfig.Entry e) {
        if (!AuthManager.requireAuth(player)) {
            return;
        }
        Stat stat = currencyStat(s);
        String name = player.getName().getString();

        ItemStack stack = build(player, e);
        if (stack == null) {
            say(player, "Bu ürün şu an satın alınamıyor (shop.json hatası).", ChatFormatting.RED);
            return;
        }

        // önce bedeli al, sonra eşyayı ver
        if (!DonutMod.store().spend(player.getUUID(), name, stat, e.price)) {
            say(player, "Yetersiz " + currencyName(s) + ". Gerekli: " + DonutCommands.fmt(e.price),
                    ChatFormatting.RED);
            return;
        }

        int count = stack.getCount();
        Component itemName = stack.getHoverName();
        boolean dropped = DonutCommands.giveItem(player, stack);

        player.sendSystemMessage(Component.literal("Satın aldın: " + count + "x ")
                .append(itemName)
                .append(Component.literal(" (-" + DonutCommands.fmt(e.price) + " " + currencyName(s) + ")"))
                .withStyle(ChatFormatting.GREEN));
        if (dropped) {
            say(player, "Envanterin doluydu, bir kısmı yere düştü.", ChatFormatting.YELLOW);
        }
    }

    // ---------------------------------------------------------------- /sell

    public static void openSell(ServerPlayer player) {
        if (!AuthManager.requireAuth(player)) {
            return;
        }
        if (!DonutMod.shop().sellEnabled) {
            say(player, "/sell şu an kapalı.", ChatFormatting.RED);
            return;
        }
        SimpleContainer box = new SimpleContainer(54);
        player.openMenu(new SimpleMenuProvider((id, inv, p) -> new SellMenu(id, inv, box),
                plain("Sat (kapatınca satılır)")));
        say(player, "Satmak istediklerini koy, sandığı kapatınca " + DonutMod.config().moneyName
                + " alırsın. Satılamayan eşyalar geri verilir.", ChatFormatting.GRAY);
    }

    static void finishSale(ServerPlayer player, SimpleContainer box) {
        ShopConfig sc = DonutMod.shop();
        long total = 0;
        long soldItems = 0;
        List<ItemStack> back = new ArrayList<>();

        for (int i = 0; i < box.getContainerSize(); i++) {
            ItemStack s = box.getItem(i);
            if (s.isEmpty()) {
                continue;
            }
            box.setItem(i, ItemStack.EMPTY); // önce sandıktan al
            Long price = sc.sellEnabled ? sc.sellPrices.get(idOf(s.getItem())) : null;
            if (price == null || price <= 0) {
                back.add(s);
            } else {
                total = Math.min(DataStore.MAX, total + Math.min(price, 1_000_000_000_000L) * s.getCount());
                soldItems += s.getCount();
            }
        }

        // satılamayanları geri ver
        for (ItemStack b : back) {
            DonutCommands.giveItem(player, b);
        }

        if (total > 0) {
            DonutMod.store().earn(player.getUUID(), player.getName().getString(), Stat.MONEY, total);
            say(player, soldItems + " eşya sattın: +" + DonutCommands.fmt(total) + " "
                    + DonutMod.config().moneyName, ChatFormatting.GREEN);
        }
        if (!back.isEmpty()) {
            say(player, "Satılamayan eşyalar geri verildi.", ChatFormatting.YELLOW);
        }
    }

    // ------------------------------------------------------------ eşya kurma

    static ItemStack build(ServerPlayer player, ShopConfig.Entry e) {
        Item item = findItem(nz(e.item));
        if (item == null) {
            DonutMod.LOGGER.warn("shop.json: bilinmeyen eşya '{}'", e.item);
            return null;
        }
        ItemStack st = new ItemStack(item, Math.max(1, e.count));
        st.setCount(Math.min(st.getCount(), st.getMaxStackSize()));

        if (e.name != null && !e.name.isBlank()) {
            st.set(DataComponents.CUSTOM_NAME, plain(e.name));
        }
        if (e.enchants != null) {
            for (Map.Entry<String, Integer> en : e.enchants.entrySet()) {
                Holder<Enchantment> h = findEnchant(player, en.getKey());
                if (h == null) {
                    DonutMod.LOGGER.warn("shop.json: bilinmeyen büyü '{}'", en.getKey());
                } else {
                    st.enchant(h, en.getValue());
                }
            }
        }
        return st;
    }

    private static ItemStack display(ServerPlayer player, ShopConfig.Section s, ShopConfig.Entry e) {
        ItemStack st = build(player, e);
        if (st == null) {
            return null;
        }
        List<Component> lore = new ArrayList<>();
        lore.add(plain("Fiyat: " + DonutCommands.fmt(e.price) + " " + currencyName(s)).withStyle(ChatFormatting.YELLOW));
        lore.add(plain("Satın almak için tıkla").withStyle(ChatFormatting.GRAY));
        st.set(DataComponents.LORE, new ItemLore(lore));
        return st;
    }

    private static Holder<Enchantment> findEnchant(ServerPlayer player, String id) {
        String want = id.contains(":") ? id : "minecraft:" + id;
        HolderLookup.RegistryLookup<Enchantment> lookup =
                player.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        return lookup.listElements()
                .filter(h -> want.equals(keyString(String.valueOf(h.key()))))
                .findFirst()
                .orElse(null);
    }

    /** "ResourceKey[minecraft:enchantment / minecraft:sharpness]" -> "minecraft:sharpness" */
    private static String keyString(String raw) {
        int i = raw.lastIndexOf(" / ");
        if (i < 0) {
            return raw;
        }
        String s = raw.substring(i + 3);
        return s.endsWith("]") ? s.substring(0, s.length() - 1) : s;
    }

    static Item findItem(String id) {
        if (itemIndex == null) {
            Map<String, Item> idx = new HashMap<>();
            for (Item i : BuiltInRegistries.ITEM) {
                idx.put(idOf(i), i);
            }
            itemIndex = idx;
        }
        return itemIndex.get(id.contains(":") ? id : "minecraft:" + id);
    }

    static String idOf(Item item) {
        return String.valueOf(BuiltInRegistries.ITEM.getKey(item));
    }

    // --------------------------------------------------------------- yardımcı

    private static boolean isMoney(ShopConfig.Section s) {
        return "para".equalsIgnoreCase(s.currency) || "money".equalsIgnoreCase(s.currency);
    }

    private static Stat currencyStat(ShopConfig.Section s) {
        return isMoney(s) ? Stat.MONEY : Stat.SHARD;
    }

    private static String currencyName(ShopConfig.Section s) {
        DonutConfig cfg = DonutMod.config();
        return isMoney(s) ? cfg.moneyName : cfg.shardName;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static MutableComponent plain(String text) {
        return Component.literal(text).withStyle(st -> st.withItalic(false));
    }

    private static void say(ServerPlayer p, String msg, ChatFormatting color) {
        p.sendSystemMessage(Component.literal(msg).withStyle(color));
    }

    private static ItemStack icon(String itemId, String title, ChatFormatting color) {
        Item item = findItem(nz(itemId));
        ItemStack st = new ItemStack(item == null ? Items.BARRIER : item);
        st.set(DataComponents.CUSTOM_NAME, plain(title).withStyle(color));
        return st;
    }

    private static ItemStack button(Item item, String title, ChatFormatting color) {
        ItemStack st = new ItemStack(item);
        st.set(DataComponents.CUSTOM_NAME, plain(title).withStyle(color));
        return st;
    }

    private static void fill(SimpleContainer c) {
        for (int i = 0; i < c.getContainerSize(); i++) {
            ItemStack f = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
            f.set(DataComponents.CUSTOM_NAME, Component.literal(" "));
            c.setItem(i, f);
        }
    }
}
