package net.neoforged.jst.parchment.namesanddocs.parchment;

import net.neoforged.jst.parchment.namesanddocs.NamesAndDocsForMethod;
import net.neoforged.jst.parchment.namesanddocs.NamesAndDocsForParameter;
import org.parchmentmc.feather.mapping.MappingDataContainer;

import java.util.List;

class ParchmentNamesAndDocsForMethod implements NamesAndDocsForMethod {
    private final MappingDataContainer.MethodData methodData;

    public ParchmentNamesAndDocsForMethod(MappingDataContainer.MethodData methodData) {
        this.methodData = methodData;
    }

    @Override
    public List<String> getJavadoc() {
        return methodData.getJavadoc();
    }

    @Override
    public NamesAndDocsForParameter getParameter(int index, int jvmIndex) {
        var paramData = methodData.getParameter((byte) jvmIndex);
        return paramData != null ? new ParchmentNamesAndDocsForParameter(paramData) : null;
    }
}
