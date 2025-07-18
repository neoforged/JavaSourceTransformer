package com.example;

public class Example {
    public static final int
            RED = 0xFF0000,
            PURPLE = 0x800080,
            PINK = 0xFFC0CB;

    public static void acceptColor(int in) {
        int color = 14123903;
        if (in < 0) {
            color = 8388736;
        } else {
            color = in == 0 ? 16711680 : 16761035;
        }

        setColor(color);
    }

    public static void setColor(int color) {}
}
