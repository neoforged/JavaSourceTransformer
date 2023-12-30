package net.neoforged.jst.parchment.namesanddocs;

import net.neoforged.jst.parchment.namesanddocs.srgutils.MappingFileDatabase;
import net.neoforged.jst.parchment.namesanddocs.parchment.ParchmentDatabase;
import net.neoforged.srgutils.INamedMappingFile;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;

public final class NameAndDocSourceLoader {
    private NameAndDocSourceLoader() {
    }

    public static NamesAndDocsDatabase load(Path path) throws IOException {
        return load(path, null);
    }

    public static NamesAndDocsDatabase load(Path path, @Nullable NameAndDocsFormat format) throws IOException {
        format = Objects.requireNonNullElseGet(format, () -> guessFormat(path));

        return switch (format) {
            case PARCHMENT_ZIP -> ParchmentDatabase.loadZip(path);
            case PARCHMENT_JSON -> ParchmentDatabase.loadJson(path);
            case TSRG2 -> MappingFileDatabase.load(path);
        };
    }

    private static NameAndDocsFormat guessFormat(Path path) {
        var filename = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (filename.endsWith(".zip")) {
            return NameAndDocsFormat.PARCHMENT_ZIP;
        } else if (filename.endsWith(".json")) {
            return NameAndDocsFormat.PARCHMENT_JSON;
        } else if (filename.endsWith(".tsrg")) {
            return NameAndDocsFormat.TSRG2;
        }
        throw new IllegalArgumentException("Don't know how to load " + path);
    }

}
