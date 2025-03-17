package com.example;

public class Example {

    void execute() {
        acceptHex(0xA505);
        acceptBin(0b1010100111010110000);
        acceptOct(017350);
        acceptChar('d');
    }

    void acceptHex(int hex) {}
    void acceptBin(int b) {}
    void acceptOct(int oct) {}

    void acceptChar(char c) {}
}
