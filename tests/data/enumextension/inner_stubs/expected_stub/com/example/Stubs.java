package com.example;

import net.neoforged.fml.common.asm.enumextension.EnumProxy;

public class Stubs {
    public class Inner {
        public static final EnumProxy A = null;
        public static final EnumProxy B = null;
        public class SubInner {
            public static final EnumProxy C = null;
        }
    }
}
