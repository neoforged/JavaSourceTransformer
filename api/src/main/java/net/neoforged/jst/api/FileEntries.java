package net.neoforged.jst.api;

import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class FileEntries {
    private FileEntries() {
    }

    /**
     * Creates a file entry for a given NIO path.
     * Since file entries need to know their path relative to the source root, the source root has to be
     * given as an additional parameter.
     */
    public static FileEntry ofPath(Path sourceRoot, Path path) {
        if (path.equals(sourceRoot)) {
            throw new IllegalStateException("path must not be the source root itself, since this results in an empty relative path");
        }
        if (!path.startsWith(sourceRoot)) {
            throw new IllegalStateException("path must be a child of sourceRoot");
        }
        return new PathFileEntry(sourceRoot, path);
    }

    /**
     * Creates a file entry for an existing zip entry. Will source the content from the given zip file.
     */
    public static FileEntry ofZipEntry(ZipFile zipFile, ZipEntry zipEntry) {
        return new ZipFileEntry(zipFile, zipEntry);
    }
}
