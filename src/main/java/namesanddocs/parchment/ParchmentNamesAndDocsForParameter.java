package namesanddocs.parchment;

import namesanddocs.NamesAndDocsForParameter;
import org.parchmentmc.feather.mapping.MappingDataContainer;

class ParchmentNamesAndDocsForParameter implements NamesAndDocsForParameter {
    private final MappingDataContainer.ParameterData parameterData;

    public ParchmentNamesAndDocsForParameter(MappingDataContainer.ParameterData parameterData) {
        this.parameterData = parameterData;
    }

    @Override
    public String getName() {
        return parameterData.getName();
    }
}
