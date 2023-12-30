package net.neoforged.jst.parchment.namesanddocs.srgutils;

import net.neoforged.jst.parchment.namesanddocs.NamesAndDocsForClass;
import net.neoforged.jst.parchment.namesanddocs.NamesAndDocsForField;
import net.neoforged.jst.parchment.namesanddocs.NamesAndDocsForMethod;
import net.neoforged.srgutils.IMappingFile;

import java.util.List;

class MappingFileClassData implements NamesAndDocsForClass {
    private final IMappingFile.IClass classData;

    public MappingFileClassData(IMappingFile.IClass classData) {

        this.classData = classData;
    }

    @Override
    public List<String> getJavadoc() {
        return List.of();
    }

    @Override
    public NamesAndDocsForField getField(String name) {
        return null;
    }

    @Override
    public NamesAndDocsForMethod getMethod(String name, String methodSignature) {
        var methodData = classData.getMethod(name, methodSignature);
        return methodData != null ? new MappingFileMethodData(methodData) : null;
    }
}
