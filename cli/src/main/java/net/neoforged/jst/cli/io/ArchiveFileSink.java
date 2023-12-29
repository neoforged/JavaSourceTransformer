package net.neoforged.jst.cli.io;

import net.neoforged.jst.api.FileSink;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

class ArchiveFileSink implements FileSink {
    private final ZipOutputStream zout;

    public ArchiveFileSink(Path path) throws IOException {
        this.zout = new ZipOutputStream(Files.newOutputStream(path));
    }

    @Override
    public boolean isOrdered() {
        return false;
    }

    @Override
    public void putDirectory(String relativePath) throws IOException {
        if (!relativePath.endsWith("/")) {
            relativePath += "/";
        }

        var ze = new ZipEntry(relativePath);
        zout.putNextEntry(ze);
        zout.closeEntry();
    }

    @Override
    public void putFile(String relativePath, FileTime lastModified, byte[] content) throws IOException {
        var ze = new ZipEntry(relativePath);
        ze.setLastModifiedTime(lastModified);
        zout.putNextEntry(ze);
        zout.write(content);
        zout.closeEntry();
    }

    @Override
    public void close() throws IOException {
        this.zout.close();
    }

    @Override
    public boolean canHaveMultipleEntries() {
        return true;
    }
}
