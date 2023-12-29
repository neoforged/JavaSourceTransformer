package net.neoforged.jst.api;

public record TransformContext(IntelliJEnvironment environment, FileSource source, FileSink sink) {
}
