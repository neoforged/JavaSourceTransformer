package net.neoforged.jst.parchment.namesanddocs.srgutils;

import net.neoforged.jst.parchment.namesanddocs.NamesAndDocsDatabase;
import net.neoforged.jst.parchment.namesanddocs.NamesAndDocsForClass;
import net.neoforged.srgutils.IMappingFile;
import net.neoforged.srgutils.INamedMappingFile;

import java.io.IOException;
import java.nio.file.Path;

public class MappingFileDatabase implements NamesAndDocsDatabase {
    private final IMappingFile tree;

    public MappingFileDatabase(INamedMappingFile tree) {
        this.tree = tree.getMap("right", "right");
    }

    public static MappingFileDatabase load(Path path) throws IOException {
        var mappingFile = INamedMappingFile.load(path.toFile());
        return new MappingFileDatabase(mappingFile);
    }

    @Override
    public NamesAndDocsForClass getClass(String className) {
        var classData = tree.getClass(className);
        return classData != null ? new MappingFileClassData(classData) : null;
    }
}
