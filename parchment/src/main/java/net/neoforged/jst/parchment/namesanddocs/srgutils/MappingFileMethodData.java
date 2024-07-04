package net.neoforged.jst.parchment.namesanddocs.srgutils;

import net.neoforged.jst.parchment.namesanddocs.NamesAndDocsForMethod;
import net.neoforged.jst.parchment.namesanddocs.NamesAndDocsForParameter;
import net.neoforged.srgutils.IMappingFile;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class MappingFileMethodData implements NamesAndDocsForMethod {
    private final IMappingFile.IMethod methodData;

    public MappingFileMethodData(IMappingFile.IMethod methodData) {
        this.methodData = methodData;
    }

    @Override
    public List<String> getJavadoc() {
        return List.of();
    }

    @Override
    public NamesAndDocsForParameter getParameter(int index, int jvmIndex) {
        var paramData = methodData.getParameter(index);
        if (paramData == null || paramData.getMapped() == null) {
            return null;
        }
        return new NamesAndDocsForParameter() {
            @Override
            public @Nullable String getName() {
                return paramData.getOriginal();
            }

            @Override
            public @Nullable String getJavadoc() {
                return null;
            }
        };
    }
}
