package net.neoforged.jst.cli.io;

import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

class ZipFileSourceEntry implements SourceEntry {
    private final ZipFile zipFile;
    private final ZipEntry zipEntry;

    public ZipFileSourceEntry(ZipFile zipFile, ZipEntry zipEntry) {
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
    public long lastModified() {
        return zipEntry.getLastModifiedTime().toMillis();
    }

    @Override
    public InputStream openInputStream() throws IOException {
        return zipFile.getInputStream(zipEntry);
    }
}
