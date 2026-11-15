package com.thexyro.donutmod;

/** Admin komutlarındaki işlem türü. */
public enum Op {
    SET("set"),
    ADD("add"),
    REMOVE("remove");

    public final String command;

    Op(String command) {
        this.command = command;
    }
}
