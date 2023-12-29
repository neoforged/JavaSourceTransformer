package net.neoforged.jst.cli.io;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import net.neoforged.jst.api.FileSource;
import net.neoforged.jst.api.FileEntry;

import java.nio.file.Path;
import java.util.stream.Stream;

public record SingleFileSource(Path path) implements FileSource, AutoCloseable {
    @Override
    public VirtualFile createSourceRoot(VirtualFileManager vfsManager) {
        return vfsManager.findFileByNioPath(path.getParent());
    }

    @Override
    public Stream<FileEntry> streamEntries() {
        return Stream.of(new PathEntry(path.getParent(), path));
    }

    @Override
    public boolean isOrdered() {
        return false;
    }
}
