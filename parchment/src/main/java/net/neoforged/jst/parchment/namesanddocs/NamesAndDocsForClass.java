package net.neoforged.jst.parchment.namesanddocs;

import java.util.List;

public interface NamesAndDocsForClass {
    List<String> getJavadoc();

    NamesAndDocsForField getField(String name);

    NamesAndDocsForMethod getMethod(String name, String methodSignature);
}
