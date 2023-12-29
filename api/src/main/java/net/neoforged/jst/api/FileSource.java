package net.neoforged.jst.api;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;

import java.io.IOException;
import java.util.stream.Stream;

public interface FileSource extends AutoCloseable {
    VirtualFile createSourceRoot(VirtualFileManager vfsManager);

    Stream<FileEntry> streamEntries() throws IOException;

    boolean canHaveMultipleEntries();

    boolean isOrdered();

    @Override
    default void close() throws IOException {
    }
}
