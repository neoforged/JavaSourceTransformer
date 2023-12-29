package net.neoforged.jst.cli.io;

import net.neoforged.jst.api.FileEntry;
import net.neoforged.jst.api.FileSink;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

public record SingleFileSink(Path path) implements FileSink {

    @Override
    public void put(FileEntry entry, byte[] content) throws IOException {
        Path targetPath;
        if (Files.isDirectory(path)) {
            targetPath = path.resolve(entry.relativePath());
        } else {
            targetPath = path;
        }
        Files.write(targetPath, content);
        Files.setLastModifiedTime(path, FileTime.fromMillis(entry.lastModified()));
    }

    @Override
    public boolean isOrdered() {
        return false;
    }
}
