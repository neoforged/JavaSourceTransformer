package net.neoforged.jst.cli.io;

import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import net.neoforged.jst.api.FileEntries;
import net.neoforged.jst.api.FileEntry;
import net.neoforged.jst.api.FileSource;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import java.util.zip.ZipFile;

class ArchiveFileSource implements FileSource {
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
        var spliterator = Spliterators.spliterator(
                zipFile.entries().asIterator(),
                zipFile.size(),
                Spliterator.IMMUTABLE | Spliterator.ORDERED
        );
        return StreamSupport.stream(spliterator, false)
                .map(ze -> FileEntries.ofZipEntry(zipFile, ze));
    }

    @Override
    public boolean canHaveMultipleEntries() {
        return true;
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
