package net.neoforged.jst.cli.io;

import net.neoforged.jst.api.FileSink;
import net.neoforged.jst.api.FileSource;
import net.neoforged.jst.cli.PathType;

import java.io.IOException;
import java.nio.file.Path;

public final class FileSinks {
    private FileSinks() {
    }

    public static FileSink create(Path path, PathType format, FileSource source) throws IOException {
        if (format == PathType.AUTO) {
            if (source instanceof SingleFileSource) {
                format = PathType.SINGLE_FILE;
            } else if (source instanceof ArchiveFileSource) {
                format = PathType.ARCHIVE;
            } else if (source instanceof FolderFileSource) {
                format = PathType.FOLDER;
            } else {
                throw new IllegalArgumentException("Cannot auto-detect output format based on source: " + source.getClass());
            }
        }

        return switch (format) {
            case AUTO -> throw new IllegalArgumentException("Do not support AUTO for output when input also was AUTO!");
            case SINGLE_FILE -> new SingleFileSink(path);
            case ARCHIVE -> new ArchiveFileSink(path);
            case FOLDER -> new FolderFileSink(path);
        };
    }
}
