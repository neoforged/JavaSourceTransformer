package net.neoforged.jst.cli.io;

import net.neoforged.jst.cli.PathType;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class Sink implements AutoCloseable {
    private final Path path;
    private final PathType format;
    @Nullable
    private final ZipOutputStream zout;

    public Sink(Source source, Path outputPath, PathType outputFormat) throws IOException {
        this.format = outputFormat == PathType.AUTO ? source.getFormat() : outputFormat;
        this.path = outputPath;
        this.zout = this.format == PathType.ARCHIVE ? new ZipOutputStream(Files.newOutputStream(outputPath)) : null;
    }

    public void put(SourceEntry entry, byte[] content) throws IOException {
        put(entry, new ByteArrayInputStream(content));
    }

    public void put(SourceEntry entry, InputStream content) throws IOException {
        switch (format) {
            case SINGLE_FILE -> {
                Path targetPath;
                if (Files.isDirectory(path)) {
                    targetPath = path.resolve(entry.relativePath());
                } else {
                    targetPath = path;
                }
                try (var out = Files.newOutputStream(targetPath)) {
                    content.transferTo(out);
                }
                Files.setLastModifiedTime(path, FileTime.fromMillis(entry.lastModified()));
            }
            case ARCHIVE -> {
                if (zout != null) {
                    var ze = new ZipEntry(entry.relativePath());
                    ze.setLastModifiedTime(FileTime.from(Instant.now()));
                    zout.putNextEntry(ze);
                    content.transferTo(zout);
                    zout.closeEntry();
                }
            }
            case FOLDER -> {
                try (var out = Files.newOutputStream(path.resolve(entry.relativePath()))) {
                    content.transferTo(out);
                }
                Files.setLastModifiedTime(path, FileTime.fromMillis(entry.lastModified()));
            }
            default -> throw new IllegalStateException("Unexpected format: " + format);
        }
    }

    @Override
    public void close() throws Exception {
        if (zout != null) {
            zout.close();
        }
    }

    public boolean isOrdered() {
        return format == PathType.ARCHIVE;
    }
}
