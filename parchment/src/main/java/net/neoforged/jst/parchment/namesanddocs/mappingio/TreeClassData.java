package net.neoforged.jst.parchment.namesanddocs.mappingio;

import net.fabricmc.mappingio.tree.MappingTree;
import net.neoforged.jst.parchment.namesanddocs.NamesAndDocsForClass;
import net.neoforged.jst.parchment.namesanddocs.NamesAndDocsForField;
import net.neoforged.jst.parchment.namesanddocs.NamesAndDocsForMethod;

import java.util.List;

class TreeClassData implements NamesAndDocsForClass {
    private final MappingTree.ClassMapping classData;

    public TreeClassData(MappingTree.ClassMapping classData) {

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
        var methodData = classData.getMethod(name, methodSignature, 0);
        return methodData != null ? new TreeMethodData(methodData) : null;
    }
}
