package net.neoforged.jst.api;

public record TransformContext(IntelliJEnvironment environment, FileSource source, FileSink sink, Logger logger, ProblemReporter problemReporter) {
    public TransformContext(IntelliJEnvironment environment, FileSource source, FileSink sink, Logger logger) {
        this(environment, source, sink, logger, ProblemReporter.NOOP);
    }
}
