package com.example;

public class Example {
    public static final String S = "a\nb\"", S2 = "ab\\";

    public static void main(String[] args) {
        String s = Example.S, s1 = Example.S2 + Example.S, s2 = Example.S + "\" adf";
        String noUnpick = "a\\nb\\\"";
    }
}
