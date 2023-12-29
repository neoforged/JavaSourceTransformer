package net.neoforged.jst.parchment.namesanddocs;

import java.util.List;

public interface NamesAndDocsForMethod {
    List<String> getJavadoc();

    NamesAndDocsForParameter getParameter(int index);
}
