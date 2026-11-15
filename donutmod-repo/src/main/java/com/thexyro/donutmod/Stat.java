package com.thexyro.donutmod;

/** Admin'in düzenleyebildiği istatistikler. */
public enum Stat {
    MONEY("para"),
    SHARD("shard"),
    PLAYTIME("sure"),
    KILLS("kill"),
    DEATHS("death");

    public final String command;

    Stat(String command) {
        this.command = command;
    }

    public long get(PlayerData d) {
        return switch (this) {
            case MONEY -> d.money;
            case SHARD -> d.shards;
            case PLAYTIME -> d.playtimeSeconds;
            case KILLS -> d.kills;
            case DEATHS -> d.deaths;
        };
    }

    public void set(PlayerData d, long v) {
        switch (this) {
            case MONEY -> d.money = v;
            case SHARD -> d.shards = v;
            case PLAYTIME -> d.playtimeSeconds = v;
            case KILLS -> d.kills = v;
            case DEATHS -> d.deaths = v;
        }
    }
}
