package namesanddocs.mappingio;

import namesanddocs.NamesAndDocsDatabase;
import namesanddocs.NamesAndDocsForClass;
import net.fabricmc.mappingio.tree.MemoryMappingTree;

public class TreeData implements NamesAndDocsDatabase {
    private final MemoryMappingTree tree;

    public TreeData(MemoryMappingTree tree) {
        this.tree = tree;
    }

    @Override
    public NamesAndDocsForClass getClass(String className) {
        var classData = tree.getClass(className, 0);
        return classData != null ? new TreeClassData(classData) : null;
    }
}
