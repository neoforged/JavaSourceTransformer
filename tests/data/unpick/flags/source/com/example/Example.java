package com.example;

public class Example {
    public static final int
            FLAG_1 = 2,
            FLAG_2 = 4,
            FLAG_3 = 8,
            FLAG_4 = 16;

    public static void main(String[] args) {
        applyFlags(2);
        applyFlags(6);
        applyFlags(155);
        applyFlags(-1);
    }

    public static void applyFlags(int flags) {}
}
