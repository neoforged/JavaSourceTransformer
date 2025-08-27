package net.neoforged.jst.cli.io;

import net.neoforged.jst.api.FileSink;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

record FolderFileSink(Path path) implements FileSink {
    @Override
    public void putDirectory(String relativePath) throws IOException {
        var targetPath = path.resolve(relativePath);
        Files.createDirectories(targetPath);
    }

    @Override
    public void putFile(String relativePath, FileTime lastModified, byte[] content) throws IOException {
        var targetPath = path.resolve(relativePath);

        if (targetPath.getParent() != null && !Files.isDirectory(targetPath.getParent())) {
            Files.createDirectories(targetPath.getParent());
        }

        Files.write(targetPath, content);
        Files.setLastModifiedTime(targetPath, lastModified);
    }

    @Override
    public boolean canHaveMultipleEntries() {
        return true;
    }

    @Override
    public boolean isOrdered() {
        return false;
    }
}
