package net.neoforged.jst.api;

import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

public record ProblemLocation(Path file, @Nullable Integer line, @Nullable Integer column,
                              @Nullable Integer offset, @Nullable Integer length) {
    public static ProblemLocation ofFile(Path file) {
        return new ProblemLocation(file, null, null, null, null);
    }

    /**
     * @param line 1-based line number.
     */
    public static ProblemLocation ofLocationInFile(Path file, int line) {
        return new ProblemLocation(file, line, null, null, null);
    }

    /**
     * @param line   1-based line number.
     * @param column 1-based column number.
     */
    public static ProblemLocation ofLocationInFile(Path file, int line, int column) {
        return new ProblemLocation(file, line, column, null, null);
    }

    /**
     * @param line   1-based line number.
     * @param column 1-based column number.
     */
    public static ProblemLocation ofLocationInFile(Path file, int line, int column, int length) {
        return new ProblemLocation(file, line, column, null, length);
    }

    /**
     * @param offset 0-based byte offset into the file.
     */
    public static ProblemLocation ofOffsetInFile(Path file, int offset) {
        return new ProblemLocation(file, null, null, offset, null);
    }

    /**
     * @param offset 0-based byte offset into the file.
     */
    public static ProblemLocation ofOffsetInFile(Path file, int offset, int length) {
        return new ProblemLocation(file, null, null, offset, length);
    }
}
