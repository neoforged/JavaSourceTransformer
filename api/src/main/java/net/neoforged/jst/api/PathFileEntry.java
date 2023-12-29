package net.neoforged.jst.api;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

final class PathFileEntry implements FileEntry {
    private final Path path;
    private final String relativePath;
    private final boolean directory;
    private final FileTime lastModified;

    public PathFileEntry(Path relativeTo, Path path) {
        this.directory = Files.isDirectory(path);
        this.path = path;
        var relativized = relativeTo.relativize(path).toString();
        relativized = relativized.replace('\\', '/');
        this.relativePath = relativized;
        try {
            this.lastModified = Files.getLastModifiedTime(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public boolean directory() {
        return directory;
    }

    @Override
    public String relativePath() {
        return relativePath;
    }

    @Override
    public FileTime lastModified() {
        return lastModified;
    }

    @Override
    public InputStream openInputStream() throws IOException {
        return Files.newInputStream(path);
    }
}
