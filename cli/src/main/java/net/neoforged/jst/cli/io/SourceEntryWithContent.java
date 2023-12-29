package net.neoforged.jst.cli.io;

import net.neoforged.jst.api.FileEntry;

import java.io.InputStream;

public record SourceEntryWithContent(FileEntry sourceEntry, InputStream contentStream) {
}
