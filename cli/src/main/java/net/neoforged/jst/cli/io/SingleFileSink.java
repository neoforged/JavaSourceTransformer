package net.neoforged.jst.cli.io;

import net.neoforged.jst.api.FileSink;
import net.neoforged.jst.api.SourceTransformer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

record SingleFileSink(Path path) implements FileSink {

    @Override
    public void putDirectory(String relativePath) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void putFile(String relativePath, FileTime lastModified, byte[] content) throws IOException {
        Path targetPath;
        if (Files.isDirectory(path)) {
            targetPath = path.resolve(relativePath);
        } else {
            targetPath = path;
        }
        Files.write(targetPath, content);
        Files.setLastModifiedTime(targetPath, lastModified);
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
