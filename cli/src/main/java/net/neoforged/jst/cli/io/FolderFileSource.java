package net.neoforged.jst.cli.io;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import net.neoforged.jst.api.FileSource;
import net.neoforged.jst.api.FileEntry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

public record FolderFileSource(Path path) implements FileSource, AutoCloseable {
    @Override
    public VirtualFile createSourceRoot(VirtualFileManager vfsManager) {
        return vfsManager.findFileByNioPath(path);
    }

    @Override
    public Stream<FileEntry> streamEntries() throws IOException {
        return Files.walk(path)
                .map(child -> new PathEntry(path, child));
    }

    @Override
    public boolean isOrdered() {
        return false; // We currently do not guarantee ordering of the file-tree
    }
}
