package net.neoforged.jst.api;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

public interface FileSink extends AutoCloseable {
    @Override
    default void close() throws IOException {
    }

    boolean isOrdered();

    void put(FileEntry entry, byte[] content) throws IOException;
}
