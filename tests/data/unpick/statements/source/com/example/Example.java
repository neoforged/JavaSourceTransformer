package com.example;

public class Example {
    public static final int
            ONE = 1 << 0,
            TWO = 1 << 1,
            THREE = 1 << 2,
            FOUR = 1 << 3;

    public static void run(int val) {
        accept(((val & 8) != 0) ? val | 4 : val | 1);

        val = 2;

        accept(val);
    }

    public static void accept(int value) {}
}
