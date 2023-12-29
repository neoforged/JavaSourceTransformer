package net.neoforged.jst.parchment.namesanddocs.mappingio;

import net.fabricmc.mappingio.tree.MemoryMappingTree;
import net.neoforged.jst.parchment.namesanddocs.NamesAndDocsDatabase;
import net.neoforged.jst.parchment.namesanddocs.NamesAndDocsForClass;

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
