package net.neoforged.jst.api;

import java.io.IOException;
import java.nio.file.attribute.FileTime;

public interface FileSink extends AutoCloseable {
    @Override
    default void close() throws IOException {
    }

    boolean canHaveMultipleEntries();

    boolean isOrdered();

    void putDirectory(String relativePath) throws IOException;

    void putFile(String relativePath, FileTime lastModified, byte[] content) throws IOException;
}
