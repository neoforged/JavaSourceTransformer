package namesanddocs.parchment;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import namesanddocs.NamesAndDocsDatabase;
import namesanddocs.NamesAndDocsForClass;
import namesanddocs.NamesAndDocsForField;
import namesanddocs.NamesAndDocsForMethod;
import namesanddocs.NamesAndDocsForParameter;
import org.parchmentmc.feather.io.gson.MDCGsonAdapterFactory;
import org.parchmentmc.feather.io.gson.SimpleVersionAdapter;
import org.parchmentmc.feather.mapping.MappingDataContainer;
import org.parchmentmc.feather.mapping.VersionedMappingDataContainer;
import org.parchmentmc.feather.util.SimpleVersion;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
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

    public static ParchmentDatabase load(Path parchmentFile) throws IOException {
        try (var zf = new ZipFile(parchmentFile.toFile())) {
            var parchmentJsonEntry = zf.getEntry("parchment.json");
            if (parchmentJsonEntry == null || parchmentJsonEntry.isDirectory()) {
                throw new FileNotFoundException("Could not locate parchment.json at the root of ZIP-File " + parchmentFile);
            }

            Gson gson = new GsonBuilder()
                    .registerTypeAdapterFactory(new MDCGsonAdapterFactory())
                    .registerTypeAdapter(SimpleVersion.class, new SimpleVersionAdapter())
                    .create();
            try (var inputStream = zf.getInputStream(parchmentJsonEntry)) {
                String jsonString = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                return new ParchmentDatabase(gson.fromJson(jsonString, VersionedMappingDataContainer.class));
            }
        }
    }
}

