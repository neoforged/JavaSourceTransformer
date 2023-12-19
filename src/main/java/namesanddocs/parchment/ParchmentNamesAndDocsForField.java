package namesanddocs.parchment;

import namesanddocs.NamesAndDocsForField;
import org.parchmentmc.feather.mapping.MappingDataContainer;

import java.util.List;

class ParchmentNamesAndDocsForField implements NamesAndDocsForField {
    private final MappingDataContainer.FieldData fieldData;

    public ParchmentNamesAndDocsForField(MappingDataContainer.FieldData fieldData) {
        this.fieldData = fieldData;
    }

    @Override
    public List<String> getJavadoc() {
        return fieldData.getJavadoc();
    }
}
