package net.neoforged.jst.parchment.namesanddocs.parchment;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.neoforged.jst.parchment.namesanddocs.NamesAndDocsDatabase;
import net.neoforged.jst.parchment.namesanddocs.NamesAndDocsForClass;
import org.parchmentmc.feather.io.gson.MDCGsonAdapterFactory;
import org.parchmentmc.feather.io.gson.SimpleVersionAdapter;
import org.parchmentmc.feather.mapping.VersionedMappingDataContainer;
import org.parchmentmc.feather.util.SimpleVersion;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipFile;

public class ParchmentDatabase implements NamesAndDocsDatabase {
    private final VersionedMappingDataContainer container;

    public ParchmentDatabase(VersionedMappingDataContainer container) {
        this.container = container;
    }

    @Override
    public NamesAndDocsForClass getClass(String className) {
        var classData = container.getClass(className);
        if (classData != null) {
            return new ParchmentNamesAndDocsForClass(classData);
        } else {
            return null;
        }
    }

    public static ParchmentDatabase loadZip(Path parchmentFile) throws IOException {
        try (var zf = new ZipFile(parchmentFile.toFile())) {
            var parchmentJsonEntry = zf.getEntry("parchment.json");
            if (parchmentJsonEntry == null || parchmentJsonEntry.isDirectory()) {
                throw new FileNotFoundException("Could not locate parchment.json at the root of ZIP-File " + parchmentFile);
            }

            try (var inputStream = zf.getInputStream(parchmentJsonEntry)) {
                return loadJson(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
            }
        }
    }

    public static ParchmentDatabase loadJson(Path parchmentFile) throws IOException {
        try (var reader = Files.newBufferedReader(parchmentFile)) {
            return loadJson(reader);
        }
    }

    public static ParchmentDatabase loadJson(Reader reader) {
        Gson gson = new GsonBuilder()
                .registerTypeAdapterFactory(new MDCGsonAdapterFactory())
                .registerTypeAdapter(SimpleVersion.class, new SimpleVersionAdapter())
                .create();
        return new ParchmentDatabase(gson.fromJson(reader, VersionedMappingDataContainer.class));
    }
}
