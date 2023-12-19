package namesanddocs.mappingio;

import namesanddocs.NamesAndDocsForClass;
import namesanddocs.NamesAndDocsForField;
import namesanddocs.NamesAndDocsForMethod;
import net.fabricmc.mappingio.tree.MappingTree;

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
