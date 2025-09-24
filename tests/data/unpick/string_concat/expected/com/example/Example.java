package com.example;

public class Example {
    public static final int I1 = 12;
    public static final double D1 = 4.53;

    public static final String S = "ab";
    public static final String _S = "ba";

    public static void main(String[] args) {
        String a = Example.I1 + Example.S, b = (Example._S + Example.D1) + Example.I1, c = Example.S + Example._S;
    }
}
