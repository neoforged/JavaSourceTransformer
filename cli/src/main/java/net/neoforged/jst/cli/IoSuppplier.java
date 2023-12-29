package net.neoforged.jst.cli;

import java.io.IOException;

@FunctionalInterface
public interface IoSuppplier {
    byte[] getContent() throws IOException;
}
