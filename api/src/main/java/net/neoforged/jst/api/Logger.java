package net.neoforged.jst.api;

import org.jetbrains.annotations.Nullable;

import java.io.PrintStream;
import java.util.Locale;

public class Logger {
    private final PrintStream debugOut;
    private final PrintStream errorOut;

    public Logger(@Nullable PrintStream debugOut, @Nullable PrintStream errorOut) {
        this.debugOut = debugOut;
        this.errorOut = errorOut;
    }

    public void error(String message, Object... args) {
        if (errorOut != null) {
            errorOut.printf(Locale.ROOT, message + "\n", args);
        }
    }

    public void debug(String message, Object... args) {
        if (debugOut != null) {
            debugOut.printf(Locale.ROOT, message + "\n", args);
        }
    }
}
