package com;

public class Outsider {
    private static final int DIFFERENT_CONST = 12;

    public void execute() {
        int i = 472; // This should NOT be unpicked to Example.V1

        int j = 12; // This should NOT be unpicked to DIFFERENT_CONST

        int k = 4; // This should NOT be unpicked to Example.FOUR since it's outside the package
    }

    public void anotherExecute() {
        int i = Outsider.DIFFERENT_CONST; // This should be replaced with DIFFERENT_CONST
    }
}
