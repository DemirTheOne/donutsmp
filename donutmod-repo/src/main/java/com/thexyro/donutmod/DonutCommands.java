package com.thexyro.donutmod;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.commands.arguments.item.ItemInput;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class DonutCommands {
    /** Envanterin normal slotları (hotbar dahil, zırh/offhand hariç). */
    private static final int MAIN_SLOTS = 36;

    private DonutCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> d, CommandBuildContext buildContext) {
        // /stats [oyuncu]
        d.register(Commands.literal("stats")
                .executes(c -> showStats(c.getSource(), c.getSource().getPlayerOrException()))
                .then(Commands.argument("oyuncu", EntityArgument.player())
                        .executes(c -> showStats(c.getSource(), EntityArgument.getPlayer(c, "oyuncu")))));

        // /pay <oyuncu> <miktar>
        d.register(Commands.literal("pay")
                .then(Commands.argument("oyuncu", EntityArgument.player())
                        .then(Commands.argument("miktar", LongArgumentType.longArg(1))
                                .executes(DonutCommands::pay))));

        // /gift <oyuncu>  |  /gift <oyuncu> <eşya> [adet]
        d.register(Commands.literal("gift")
                .then(Commands.argument("oyuncu", EntityArgument.player())
                        .executes(DonutCommands::giftHand)
                        .then(Commands.argument("esya", ItemArgument.item(buildContext))
                                .executes(c -> giftItem(c, 1))
                                .then(Commands.argument("adet", IntegerArgumentType.integer(1))
                                        .executes(c -> giftItem(c, IntegerArgumentType.getInteger(c, "adet")))))));

        d.register(adminTree());
    }

    // ------------------------------------------------------------------ admin

    private static boolean isAdmin(CommandSourceStack src) {
        ServerPlayer p = src.getPlayer();
        return p != null && DonutMod.config().isAdmin(p.getUUID());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> adminTree() {
        LiteralArgumentBuilder<CommandSourceStack> admin = Commands.literal("admin")
                .requires(DonutCommands::isAdmin);

        for (Stat stat : Stat.values()) {
            LiteralArgumentBuilder<CommandSourceStack> statNode = Commands.literal(stat.command);
            for (Op op : Op.values()) {
                statNode.then(Commands.literal(op.command)
                        .then(Commands.argument("oyuncu", EntityArgument.player())
                                .then(Commands.argument("miktar", LongArgumentType.longArg(0))
                                        .executes(c -> adminModify(c, stat, op)))));
            }
            admin.then(statNode);
        }

        admin.then(Commands.literal("reload").executes(c -> {
            DonutMod.reloadConfig();
            c.getSource().sendSuccess(() -> Component.literal("Config yeniden yüklendi.")
                    .withStyle(ChatFormatting.GREEN), false);
            return 1;
        }));

        return admin;
    }

    private static int adminModify(CommandContext<CommandSourceStack> c, Stat stat, Op op)
            throws CommandSyntaxException {
        CommandSourceStack src = c.getSource();
        ServerPlayer target = EntityArgument.getPlayer(c, "oyuncu");
        long value = LongArgumentType.getLong(c, "miktar");

        // süre komutta dakika, kayıtta saniye
        if (stat == Stat.PLAYTIME) {
            value = Math.min(value, DataStore.MAX / 60) * 60;
        }

        String targetName = target.getName().getString();
        long result = DonutMod.store().modify(target.getUUID(), targetName, stat, op, value);

        String shown = stat == Stat.PLAYTIME ? formatTime(result) : fmt(result);
        String label = statLabel(stat);
        src.sendSuccess(() -> Component.literal(targetName + " → " + label + ": " + shown
                + " (" + op.command + ")").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    // ------------------------------------------------------------------ stats

    private static int showStats(CommandSourceStack src, ServerPlayer target) {
        PlayerData data = DonutMod.store().get(target.getUUID(), target.getName().getString());
        src.sendSuccess(() -> Component.literal("--- " + data.name + " istatistikleri ---")
                .withStyle(ChatFormatting.GOLD), false);
        src.sendSuccess(() -> line(statLabel(Stat.MONEY), fmt(data.money)), false);
        src.sendSuccess(() -> line(statLabel(Stat.SHARD), fmt(data.shards)), false);
        src.sendSuccess(() -> line("Oyun süresi", formatTime(data.playtimeSeconds)), false);
        src.sendSuccess(() -> line("Kill", fmt(data.kills)), false);
        src.sendSuccess(() -> line("Death", fmt(data.deaths)), false);
        return 1;
    }

    // -------------------------------------------------------------------- pay

    private static int pay(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        DonutConfig cfg = DonutMod.config();
        CommandSourceStack src = c.getSource();
        ServerPlayer from = src.getPlayerOrException();
        ServerPlayer to = EntityArgument.getPlayer(c, "oyuncu");
        long amount = LongArgumentType.getLong(c, "miktar");

        if (!cfg.payEnabled) {
            return fail(src, "/pay şu an kapalı.");
        }
        if (from.getUUID().equals(to.getUUID())) {
            return fail(src, "Kendine " + cfg.moneyName + " gönderemezsin.");
        }

        String fromName = from.getName().getString();
        String toName = to.getName().getString();

        PlayerData fromData = DonutMod.store().get(from.getUUID(), fromName);
        if (fromData.money < amount) {
            return fail(src, "Yetersiz bakiye. Bakiyen: " + fmt(fromData.money) + " " + cfg.moneyName);
        }

        if (!DonutMod.store().transfer(from.getUUID(), fromName, to.getUUID(), toName, amount)) {
            return fail(src, "İşlem yapılamadı (alıcının bakiye sınırı aşılıyor olabilir).");
        }

        src.sendSuccess(() -> Component.literal(toName + " oyuncusuna " + fmt(amount) + " "
                + cfg.moneyName + " gönderdin.").withStyle(ChatFormatting.GREEN), false);
        to.sendSystemMessage(Component.literal(fromName + " sana " + fmt(amount) + " "
                + cfg.moneyName + " gönderdi.").withStyle(ChatFormatting.GREEN));
        return 1;
    }

    // ------------------------------------------------------------------- gift

    /** /gift <oyuncu> -> elindeki eşyanın tamamını verir. */
    private static int giftHand(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        CommandSourceStack src = c.getSource();
        ServerPlayer from = src.getPlayerOrException();
        ServerPlayer to = EntityArgument.getPlayer(c, "oyuncu");

        if (!DonutMod.config().giftEnabled) {
            return fail(src, "/gift şu an kapalı.");
        }
        if (from.getUUID().equals(to.getUUID())) {
            return fail(src, "Kendine eşya gönderemezsin.");
        }

        ItemStack held = from.getMainHandItem();
        if (held.isEmpty()) {
            return fail(src, "Elinde eşya yok.");
        }

        int count = held.getCount();
        Component itemName = held.getHoverName();

        ItemStack taken = held.split(count);   // önce göndericiden al
        boolean dropped = giveItem(to, taken); // sonra alıcıya ver
        from.containerMenu.broadcastChanges();

        notifyGift(src, from, to, itemName, count, dropped);
        return 1;
    }

    /** /gift <oyuncu> <eşya> [adet] -> envanterden belirtilen eşyayı verir. */
    private static int giftItem(CommandContext<CommandSourceStack> c, int amount) throws CommandSyntaxException {
        CommandSourceStack src = c.getSource();
        ServerPlayer from = src.getPlayerOrException();
        ServerPlayer to = EntityArgument.getPlayer(c, "oyuncu");
        ItemInput input = ItemArgument.getItem(c, "esya");
        Item item = input.getItem();

        if (!DonutMod.config().giftEnabled) {
            return fail(src, "/gift şu an kapalı.");
        }
        if (from.getUUID().equals(to.getUUID())) {
            return fail(src, "Kendine eşya gönderemezsin.");
        }

        Component itemName = item.getDefaultInstance().getHoverName();
        Inventory inv = from.getInventory();

        int have = 0;
        for (int i = 0; i < MAIN_SLOTS; i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty() && s.is(item)) {
                have += s.getCount();
            }
        }
        if (have < amount) {
            return fail(src, "Envanterinde yeterli eşya yok (istenen: " + amount + ", var: " + have + ").");
        }

        // önce göndericiden al
        List<ItemStack> taken = new ArrayList<>();
        int remaining = amount;
        for (int i = 0; i < MAIN_SLOTS && remaining > 0; i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty() || !s.is(item)) {
                continue;
            }
            int n = Math.min(remaining, s.getCount());
            taken.add(s.split(n));
            remaining -= n;
        }
        from.containerMenu.broadcastChanges();

        // sonra alıcıya ver
        boolean dropped = false;
        for (ItemStack st : taken) {
            dropped |= giveItem(to, st);
        }

        notifyGift(src, from, to, itemName, amount, dropped);
        return 1;
    }

    /** Eşyayı envantere koyar; sığmayan kısım yere düşer. Yere düştüyse true. */
    private static boolean giveItem(ServerPlayer target, ItemStack stack) {
        target.getInventory().add(stack);
        if (!stack.isEmpty()) {
            target.drop(stack, false);
            return true;
        }
        return false;
    }

    private static void notifyGift(CommandSourceStack src, ServerPlayer from, ServerPlayer to,
                                   Component itemName, int count, boolean dropped) {
        String fromName = from.getName().getString();
        String toName = to.getName().getString();

        src.sendSuccess(() -> Component.literal(toName + " oyuncusuna " + count + "x ")
                .append(itemName).append(Component.literal(" gönderdin."))
                .withStyle(ChatFormatting.GREEN), false);

        to.sendSystemMessage(Component.literal(fromName + " sana " + count + "x ")
                .append(itemName).append(Component.literal(" gönderdi."))
                .withStyle(ChatFormatting.GREEN));

        if (dropped) {
            to.sendSystemMessage(Component.literal("Envanterin doluydu, bir kısmı yere düştü.")
                    .withStyle(ChatFormatting.YELLOW));
        }
    }

    // ---------------------------------------------------------------- helpers

    private static int fail(CommandSourceStack src, String msg) {
        src.sendFailure(Component.literal(msg).withStyle(ChatFormatting.RED));
        return 0;
    }

    private static Component line(String label, String value) {
        return Component.literal(label + ": ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(value).withStyle(ChatFormatting.YELLOW));
    }

    private static String statLabel(Stat stat) {
        DonutConfig cfg = DonutMod.config();
        return switch (stat) {
            case MONEY -> capitalize(cfg.moneyName);
            case SHARD -> capitalize(cfg.shardName);
            case PLAYTIME -> "Oyun süresi";
            case KILLS -> "Kill";
            case DEATHS -> "Death";
        };
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        return s.substring(0, 1).toUpperCase(Locale.forLanguageTag("tr-TR")) + s.substring(1);
    }

    static String fmt(long n) {
        return NumberFormat.getIntegerInstance(Locale.forLanguageTag("tr-TR")).format(n);
    }

    static String formatTime(long seconds) {
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        return h + "sa " + m + "dk";
    }
}
