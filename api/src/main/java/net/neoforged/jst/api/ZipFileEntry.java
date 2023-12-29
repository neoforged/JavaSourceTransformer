package net.neoforged.jst.api;

import net.neoforged.jst.api.FileEntry;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.attribute.FileTime;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

class ZipFileEntry implements FileEntry {
    private final ZipFile zipFile;
    private final ZipEntry zipEntry;

    public ZipFileEntry(ZipFile zipFile, ZipEntry zipEntry) {
        this.zipFile = zipFile;
        this.zipEntry = zipEntry;
    }

    @Override
    public boolean directory() {
        return zipEntry.isDirectory();
    }

    @Override
    public String relativePath() {
        return zipEntry.getName();
    }

    @Override
    public FileTime lastModified() {
        return zipEntry.getLastModifiedTime();
    }

    @Override
    public InputStream openInputStream() throws IOException {
        return zipFile.getInputStream(zipEntry);
    }
}
