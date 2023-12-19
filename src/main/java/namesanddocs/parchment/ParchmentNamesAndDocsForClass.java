package namesanddocs.parchment;

import namesanddocs.NamesAndDocsForClass;
import namesanddocs.NamesAndDocsForField;
import namesanddocs.NamesAndDocsForMethod;
import org.parchmentmc.feather.mapping.MappingDataContainer;

import java.util.List;

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
