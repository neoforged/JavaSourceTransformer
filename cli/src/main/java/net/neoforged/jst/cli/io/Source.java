package net.neoforged.jst.cli.io;

import com.intellij.core.CoreApplicationEnvironment;
import com.intellij.openapi.vfs.VirtualFile;
import net.neoforged.jst.cli.PathType;
import org.jetbrains.annotations.Nullable;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class Source implements AutoCloseable {
    private final Path path;
    private final PathType format;
    @Nullable
    private ZipFile zf = null;
    @Nullable
    private Stream<Path> directoryStream;

    public Source(Path path, PathType format) throws IOException {
        this.path = path;

        if (!Files.exists(path)) {
            throw new FileNotFoundException("File does not exist: " + path);
        }

        this.format = switch (format) {
            case AUTO -> {
                // Directories are easy
                if (Files.isDirectory(path)) {
                    this.zf = null;
                    yield PathType.FOLDER;
                } else if (Files.isRegularFile(path)) {
                    // Try opening it as a ZIP-File first
                    try {
                        zf = new ZipFile(path.toFile());
                    } catch (IOException ignored) {
                    }
                    yield zf != null ? PathType.ARCHIVE : PathType.SINGLE_FILE;
                } else {
                    throw new IOException("Cannot detect type of " + path + " it is neither file nor folder.");
                }
            }
            case SINGLE_FILE -> {
                if (!Files.isRegularFile(path)) {
                    throw new IOException("Expected " + path + " to be a file.");
                }
                yield PathType.SINGLE_FILE;
            }
            case ARCHIVE -> {
                if (!Files.isRegularFile(path)) {
                    throw new IOException("Expected " + path + " to be a file.");
                }
                zf = new ZipFile(path.toFile());
                yield PathType.ARCHIVE;
            }
            case FOLDER -> {
                if (!Files.isDirectory(path)) {
                    throw new IOException("Expected " + path + " to be a directory.");
                }
                yield PathType.FOLDER;
            }
        };
    }

    public Path getPath() {
        return path;
    }

    public PathType getFormat() {
        return format;
    }

    public Stream<SourceEntry> streamEntries() throws IOException {
        switch (format) {
            case SINGLE_FILE -> {
                return Stream.of(new PathSourceEntry(path.getParent(), path));
            }
            case ARCHIVE -> {
                return createArchiveStream();
            }
            case FOLDER -> {
                return Files.walk(path)
                        .map(child -> new PathSourceEntry(path, child));
            }
            default -> throw new IllegalStateException("Unexpected format: " + format);
        }
    }

    private Stream<SourceEntry> createArchiveStream() {
        assert zf != null;

        Spliterator<ZipEntry> spliterator = Spliterators.spliterator(zf.entries().asIterator(), zf.size(), Spliterator.IMMUTABLE | Spliterator.ORDERED);

        return StreamSupport.stream(spliterator, false).map(ze -> new ZipFileSourceEntry(zf, ze));
    }

    public VirtualFile createSourceRoot(CoreApplicationEnvironment env) {
        return switch (format) {
            case SINGLE_FILE -> env.getLocalFileSystem().findFileByNioFile(path.getParent());
            case FOLDER -> env.getLocalFileSystem().findFileByNioFile(path);
            case ARCHIVE -> env.getJarFileSystem().findFileByPath(path.toString() + "!/");
            default -> throw new IllegalStateException("Unexpected format: " + format);
        };
    }

    @Override
    public void close() throws IOException {
        if (zf != null) {
            zf.close();
        }
        if (directoryStream != null) {
            directoryStream.close();
        }
    }

    public boolean isOrdered() {
        return format == PathType.ARCHIVE;
    }
}
