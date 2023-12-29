package net.neoforged.jst.cli.io;

import net.neoforged.jst.api.FileSource;
import net.neoforged.jst.cli.PathType;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class FileSources {
    private FileSources() {
    }

    public static FileSource create(Path path, PathType format) throws IOException {
        if (!Files.exists(path)) {
            throw new FileNotFoundException("File does not exist: " + path);
        }

        return switch (format) {
            case AUTO -> {
                // Directories are easy
                if (Files.isDirectory(path)) {
                    yield new FolderFileSource(path);
                } else if (Files.isRegularFile(path)) {
                    try {
                        // Try opening it as a ZIP-File first
                        yield new ArchiveFileSource(path);
                    } catch (IOException ignored) {
                        // Fall back to single-file
                        yield new SingleFileSource(path);
                    }
                } else {
                    throw new IOException("Cannot detect type of " + path + " it is neither file nor folder.");
                }
            }
            case FILE -> {
                if (!Files.isRegularFile(path)) {
                    throw new IOException("Expected " + path + " to be a file.");
                }
                yield new SingleFileSource(path);
            }
            case ARCHIVE -> {
                if (!Files.isRegularFile(path)) {
                    throw new IOException("Expected " + path + " to be a file.");
                }
                yield new ArchiveFileSource(path);
            }
            case FOLDER -> {
                if (!Files.isDirectory(path)) {
                    throw new IOException("Expected " + path + " to be a directory.");
                }
                yield new FolderFileSource(path);
            }
        };
    }

}
