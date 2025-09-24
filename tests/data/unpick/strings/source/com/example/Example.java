package com.example;

public class Example {
    public static final String S = "a\nb\"", S2 = "ab\\";

    public static void main(String[] args) {
        String s = "a\nb\"", s1 = "ab\\a\nb\"", s2 = "a\nb\"\" adf";
        String noUnpick = "a\\nb\\\"";
    }
}
