package namesanddocs.parchment;

import namesanddocs.NamesAndDocsForMethod;
import namesanddocs.NamesAndDocsForParameter;
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
    public NamesAndDocsForParameter getParameter(int index) {
        var paramData = methodData.getParameter((byte) index);
        return paramData != null ? new ParchmentNamesAndDocsForParameter(paramData) : null;
    }
}
