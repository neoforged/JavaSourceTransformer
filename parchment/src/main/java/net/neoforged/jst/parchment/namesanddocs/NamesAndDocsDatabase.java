package net.neoforged.jst.parchment.namesanddocs;

public interface NamesAndDocsDatabase {
    NamesAndDocsForClass getClass(String className);
}
