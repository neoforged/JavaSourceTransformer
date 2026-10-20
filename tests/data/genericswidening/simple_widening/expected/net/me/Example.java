package net.me;

import java.util.Set;

public class Example {
    public static boolean contains(Set<? extends Object> set, Object value) {
        return set.contains(value);
    }
}
