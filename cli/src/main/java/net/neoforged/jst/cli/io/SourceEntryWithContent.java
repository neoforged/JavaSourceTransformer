package net.neoforged.jst.cli.io;

import java.io.InputStream;

public record SourceEntryWithContent(SourceEntry sourceEntry, InputStream contentStream) {
}
