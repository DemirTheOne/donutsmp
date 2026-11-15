package com.thexyro.donutmod;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** config/donutmod/shop.json : market bölümleri, ürünler ve satış fiyatları. */
public class ShopConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public boolean sellEnabled = true;
    public List<Section> sections = new ArrayList<>();
    /** /sell fiyatları: eşya id -> tanesi kaç para */
    public Map<String, Long> sellPrices = new LinkedHashMap<>();

    public static class Section {
        public String id = "";
        public String title = "";
        public String icon = "minecraft:chest";
        /** "shard" veya "para" */
        public String currency = "shard";
        public int rows = 3;
        public List<Entry> items = new ArrayList<>();

        public Section() {}

        public Section(String id, String title, String icon, String currency, int rows) {
            this.id = id;
            this.title = title;
            this.icon = icon;
            this.currency = currency;
            this.rows = rows;
        }
    }

    public static class Entry {
        public int slot = 0;
        public String item = "minecraft:stone";
        public int count = 1;
        /** Boşsa eşyanın normal adı kalır. */
        public String name = "";
        /** büyü id -> seviye, ör. "minecraft:sharpness": 5 */
        public Map<String, Integer> enchants = new LinkedHashMap<>();
        public long price = 1;

        public Entry() {}

        public static Entry of(int slot, String item, int count, String name, long price) {
            Entry e = new Entry();
            e.slot = slot;
            e.item = item;
            e.count = count;
            e.name = name;
            e.price = price;
            return e;
        }
    }

    public static Path file() {
        return DonutConfig.dir().resolve("shop.json");
    }

    public static ShopConfig load() {
        Path f = file();
        if (Files.exists(f)) {
            try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
                ShopConfig c = GSON.fromJson(r, ShopConfig.class);
                if (c != null) {
                    c.fixNulls();
                    return c;
                }
            } catch (Exception e) {
                DonutMod.LOGGER.error("shop.json okunamadı, varsayılan market kullanılıyor (dosyaya dokunulmadı)", e);
                return defaults();
            }
        }
        ShopConfig d = defaults();
        d.save();
        return d;
    }

    public void save() {
        try {
            Files.createDirectories(DonutConfig.dir());
            Files.writeString(file(), GSON.toJson(this), StandardCharsets.UTF_8);
        } catch (IOException e) {
            DonutMod.LOGGER.error("shop.json yazılamadı", e);
        }
    }

    private void fixNulls() {
        if (sections == null) sections = new ArrayList<>();
        if (sellPrices == null) sellPrices = new LinkedHashMap<>();
        for (Section s : sections) {
            if (s.items == null) s.items = new ArrayList<>();
            if (s.currency == null) s.currency = "shard";
            for (Entry e : s.items) {
                if (e.enchants == null) e.enchants = new LinkedHashMap<>();
            }
        }
    }

    private static Map<String, Integer> enchants(Object... kv) {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put((String) kv[i], (Integer) kv[i + 1]);
        }
        return m;
    }

    public static ShopConfig defaults() {
        ShopConfig c = new ShopConfig();

        // --- Trimler (shard) ---
        Section trims = new Section("trims", "Trimler", "minecraft:sentry_armor_trim_smithing_template", "shard", 4);
        String[] names = {"sentry", "dune", "coast", "wild", "ward", "eye", "vex", "tide", "snout",
                "rib", "spire", "silence", "raiser", "shaper", "host", "wayfinder", "bolt", "flow"};
        for (int i = 0; i < names.length; i++) {
            trims.items.add(Entry.of(9 + i, "minecraft:" + names[i] + "_armor_trim_smithing_template", 1, "", 10));
        }
        c.sections.add(trims);

        // --- Büyülü kılıçlar (shard) ---
        Section swords = new Section("swords", "Kılıçlar", "minecraft:netherite_sword", "shard", 3);
        Entry s1 = Entry.of(11, "minecraft:netherite_sword", 1, "Keskinlik Kılıcı", 50);
        s1.enchants = enchants("minecraft:sharpness", 5, "minecraft:fire_aspect", 2, "minecraft:looting", 3,
                "minecraft:unbreaking", 3, "minecraft:mending", 1, "minecraft:sweeping_edge", 3, "minecraft:knockback", 2);
        Entry s2 = Entry.of(13, "minecraft:netherite_sword", 1, "Smite Kılıcı", 40);
        s2.enchants = enchants("minecraft:smite", 5, "minecraft:looting", 3, "minecraft:unbreaking", 3,
                "minecraft:mending", 1, "minecraft:sweeping_edge", 3);
        Entry s3 = Entry.of(15, "minecraft:netherite_sword", 1, "Bane Kılıcı", 40);
        s3.enchants = enchants("minecraft:bane_of_arthropods", 5, "minecraft:looting", 3, "minecraft:unbreaking", 3,
                "minecraft:mending", 1, "minecraft:sweeping_edge", 3);
        swords.items.add(s1);
        swords.items.add(s2);
        swords.items.add(s3);
        c.sections.add(swords);

        // --- Para marketi (para) ---
        Section money = new Section("para", "Para Marketi", "minecraft:gold_ingot", "para", 3);
        money.items.add(Entry.of(11, "minecraft:golden_apple", 4, "", 400));
        money.items.add(Entry.of(13, "minecraft:ender_pearl", 8, "", 300));
        money.items.add(Entry.of(15, "minecraft:experience_bottle", 16, "", 250));
        c.sections.add(money);

        // --- /sell fiyatları (tanesi) ---
        c.sellPrices.put("minecraft:diamond", 100L);
        c.sellPrices.put("minecraft:emerald", 60L);
        c.sellPrices.put("minecraft:netherite_ingot", 1000L);
        c.sellPrices.put("minecraft:ancient_debris", 400L);
        c.sellPrices.put("minecraft:gold_ingot", 20L);
        c.sellPrices.put("minecraft:iron_ingot", 10L);
        c.sellPrices.put("minecraft:copper_ingot", 2L);
        c.sellPrices.put("minecraft:coal", 2L);
        c.sellPrices.put("minecraft:redstone", 3L);
        c.sellPrices.put("minecraft:lapis_lazuli", 3L);
        c.sellPrices.put("minecraft:quartz", 4L);
        return c;
    }
}
