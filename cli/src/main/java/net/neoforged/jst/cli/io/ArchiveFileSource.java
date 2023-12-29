package net.neoforged.jst.cli.io;

import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import net.neoforged.jst.api.FileSource;
import net.neoforged.jst.api.FileEntry;

import java.io.IOException;
import java.nio.file.Path;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

public class ArchiveFileSource implements FileSource {
    private final Path path;
    private final ZipFile zipFile;

    public ArchiveFileSource(Path path) throws IOException {
        this.path = path;
        this.zipFile = new ZipFile(path.toFile());
    }

    @Override
    public VirtualFile createSourceRoot(VirtualFileManager vfsManager) {
        return vfsManager.getFileSystem(StandardFileSystems.JAR_PROTOCOL).findFileByPath(path.toString() + "!/");
    }

    @Override
    public Stream<FileEntry> streamEntries() {
        return zipFile.stream().map(ze -> new ZipFileEntry(zipFile, ze));
    }

    @Override
    public boolean isOrdered() {
        return true;
    }

    @Override
    public void close() throws IOException {
        zipFile.close();
    }
}
