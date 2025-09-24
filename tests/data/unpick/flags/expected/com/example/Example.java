package com.example;

public class Example {
    public static final int
            FLAG_1 = 2,
            FLAG_2 = 4,
            FLAG_3 = 8,
            FLAG_4 = 16;

    public static void main(String[] args) {
        applyFlags(Example.FLAG_1);
        applyFlags(Example.FLAG_1 | Example.FLAG_2);
        applyFlags(Example.FLAG_3 | Example.FLAG_4 | Example.FLAG_1 | 129);
        applyFlags(-1);

        applyFlags(~(Example.FLAG_1 | Example.FLAG_2));
        applyFlags(~Example.FLAG_4);
        applyFlags((Example.FLAG_4 * 21) | Example.FLAG_3);

        applyHexFlags(Example.FLAG_3 | Example.FLAG_4 | Example.FLAG_1 | 0x81);
    }

    public static void applyFlags(int flags) {}
    public static void applyHexFlags(int flags) {}
}
