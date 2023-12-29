package net.neoforged.jst.cli.io;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import net.neoforged.jst.api.FileEntries;
import net.neoforged.jst.api.FileEntry;
import net.neoforged.jst.api.FileSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

record FolderFileSource(Path path) implements FileSource, AutoCloseable {
    @Override
    public VirtualFile createSourceRoot(VirtualFileManager vfsManager) {
        return vfsManager.findFileByNioPath(path);
    }

    @Override
    public Stream<FileEntry> streamEntries() throws IOException {
        return Files.walk(path)
                .filter(p -> !p.equals(path))
                .map(child -> FileEntries.ofPath(path, child));
    }

    @Override
    public boolean canHaveMultipleEntries() {
        return true;
    }

    @Override
    public boolean isOrdered() {
        return false; // We currently do not guarantee ordering of the file-tree
    }
}
