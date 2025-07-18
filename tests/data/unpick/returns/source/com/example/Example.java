package com.example;

public class Example {
    public static final int
            ONE = 1,
            TWO = 2,
            FOUR = 4;

    public static int getNumber(boolean odd, boolean b) {
        int value = 0;
        if (odd) {
            value = 1;
        } else if (b) {
            return 4;
        } else {
            value = 2;
        }

        return value;
    }
}
