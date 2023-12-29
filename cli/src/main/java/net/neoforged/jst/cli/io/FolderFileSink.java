package net.neoforged.jst.cli.io;

import net.neoforged.jst.api.FileEntry;
import net.neoforged.jst.api.FileSink;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

public record FolderFileSink(Path path) implements FileSink {
    @Override
    public void put(FileEntry entry, byte[] content) throws IOException {
        var targetPath = path.resolve(entry.relativePath());
        Files.write(targetPath, content);
        Files.setLastModifiedTime(path, FileTime.fromMillis(entry.lastModified()));
    }

    @Override
    public boolean isOrdered() {
        return false;
    }
}
