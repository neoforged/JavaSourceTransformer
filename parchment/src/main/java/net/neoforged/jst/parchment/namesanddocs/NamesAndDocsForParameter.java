package net.neoforged.jst.parchment.namesanddocs;

import org.jetbrains.annotations.Nullable;

public interface NamesAndDocsForParameter {
    @Nullable
    String getName();

    @Nullable
    String getJavadoc();
}
