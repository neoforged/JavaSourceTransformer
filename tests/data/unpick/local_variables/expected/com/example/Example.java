package com.example;

public class Example {
    public static final int
            RED = 0xFF0000,
            PURPLE = 0x800080,
            PINK = 0xFFC0CB;

    public static void acceptColor(int in) {
        int color = 0xD7837F;
        if (in < 0) {
            color = Example.PURPLE;
        } else {
            color = in == 0x0 ? Example.RED : Example.PINK;
        }

        setColor(color);
    }

    public static void setColor(int color) {}
}
