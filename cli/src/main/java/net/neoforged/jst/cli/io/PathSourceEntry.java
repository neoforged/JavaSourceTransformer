package net.neoforged.jst.cli.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

final class PathSourceEntry implements SourceEntry {
    private final Path relativeTo;
    private final Path path;
    private final String relativePath;
    private final boolean directory;
    private final long lastModified;

    public PathSourceEntry(Path relativeTo, Path path) {
        this.directory = Files.isDirectory(path);
        this.relativeTo = relativeTo;
        this.path = path;
        var relativized = relativeTo.relativize(path).toString();
        relativized = relativized.replace('\\', '/');
        this.relativePath = relativized;
        try {
            this.lastModified = Files.getLastModifiedTime(path).toMillis();
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
    public long lastModified() {
        return lastModified;
    }

    @Override
    public InputStream openInputStream() throws IOException {
        return Files.newInputStream(path);
    }
}
