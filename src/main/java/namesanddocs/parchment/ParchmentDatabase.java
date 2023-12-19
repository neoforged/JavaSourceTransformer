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

class ParchmentNamesAndDocsForClass implements NamesAndDocsForClass {
    private final MappingDataContainer.ClassData classData;

    public ParchmentNamesAndDocsForClass(MappingDataContainer.ClassData classData) {
        this.classData = classData;
    }

    @Override
    public List<String> getJavadoc() {
        return classData.getJavadoc();
    }

    @Override
    public NamesAndDocsForField getField(String name) {
        var fieldData = classData.getField(name);
        return fieldData != null ? new ParchmentNamesAndDocsForField(fieldData) : null;
    }

    @Override
    public NamesAndDocsForMethod getMethod(String name, String methodSignature) {
        var methodData = classData.getMethod(name, methodSignature);
        return methodData != null ? new ParchmentNamesAndDocsForMethod(methodData) : null;
    }
}

class ParchmentNamesAndDocsForField implements NamesAndDocsForField {
    private final MappingDataContainer.FieldData fieldData;

    public ParchmentNamesAndDocsForField(MappingDataContainer.FieldData fieldData) {
        this.fieldData = fieldData;
    }

    @Override
    public List<String> getJavadoc() {
        return fieldData.getJavadoc();
    }
}

class ParchmentNamesAndDocsForMethod implements NamesAndDocsForMethod {
    private final MappingDataContainer.MethodData methodData;

    public ParchmentNamesAndDocsForMethod(MappingDataContainer.MethodData methodData) {
        this.methodData = methodData;
    }

    @Override
    public List<String> getJavadoc() {
        return methodData.getJavadoc();
    }

    @Override
    public NamesAndDocsForParameter getParameter(int index) {
        return null;
    }
}

class ParchmentNamesAndDocsForParameter implements NamesAndDocsForParameter {
    private final MappingDataContainer.ParameterData parameterData;

    public ParchmentNamesAndDocsForParameter(MappingDataContainer.ParameterData parameterData) {
        this.parameterData = parameterData;
    }

    @Override
    public String getName() {
        return parameterData.getName();
    }
}
