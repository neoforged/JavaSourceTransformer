package net.neoforged.jst.cli.io;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import net.neoforged.jst.api.FileEntries;
import net.neoforged.jst.api.FileEntry;
import net.neoforged.jst.api.FileSource;

import java.nio.file.Path;
import java.util.stream.Stream;

record SingleFileSource(Path path) implements FileSource, AutoCloseable {
    SingleFileSource(Path path) {
        this.path = path.toAbsolutePath();
    }

    @Override
    public VirtualFile createSourceRoot(VirtualFileManager vfsManager) {
        return vfsManager.findFileByNioPath(path.getParent());
    }

    @Override
    public Stream<FileEntry> streamEntries() {
        return Stream.of(FileEntries.ofPath(path.getParent(), path));
    }

    @Override
    public boolean canHaveMultipleEntries() {
        return false;
    }

    @Override
    public boolean isOrdered() {
        return false;
    }
}
