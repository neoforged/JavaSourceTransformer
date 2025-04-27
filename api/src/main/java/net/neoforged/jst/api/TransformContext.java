package net.neoforged.jst.api;

import net.neoforged.problems.ProblemReporter;

public record TransformContext(IntelliJEnvironment environment, FileSource source, FileSink sink, Logger logger, ProblemReporter problemReporter) {
    public TransformContext(IntelliJEnvironment environment, FileSource source, FileSink sink, Logger logger) {
        this(environment, source, sink, logger, ProblemReporter.NOOP);
    }
}
