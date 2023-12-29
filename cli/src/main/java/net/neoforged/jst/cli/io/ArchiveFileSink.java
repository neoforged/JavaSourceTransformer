package net.neoforged.jst.cli.io;

import net.neoforged.jst.api.FileSink;
import net.neoforged.jst.api.FileEntry;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ArchiveFileSink implements FileSink {
    private final ZipOutputStream zout;

    public ArchiveFileSink(Path path) throws IOException {
        this.zout = new ZipOutputStream(Files.newOutputStream(path));
    }

    @Override
    public boolean isOrdered() {
        return false;
    }

    @Override
    public void put(FileEntry entry, byte[] content) throws IOException {
        var ze = new ZipEntry(entry.relativePath());
        ze.setLastModifiedTime(FileTime.from(Instant.now()));
        zout.putNextEntry(ze);
        zout.write(content);
        zout.closeEntry();
    }

    @Override
    public void close() throws IOException {
        this.zout.close();
    }
}
