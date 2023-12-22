package namesanddocs.mappingio;

import namesanddocs.NamesAndDocsForMethod;
import namesanddocs.NamesAndDocsForParameter;
import net.fabricmc.mappingio.tree.MappingTree;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class TreeMethodData implements NamesAndDocsForMethod {
    private final MappingTree.MethodMapping methodData;

    public TreeMethodData(MappingTree.MethodMapping methodData) {
        this.methodData = methodData;
    }

    @Override
    public List<String> getJavadoc() {
        return List.of();
    }

    @Override
    public NamesAndDocsForParameter getParameter(int index) {
        var paramData = methodData.getArg(0, index, null);
        if (paramData == null || paramData.getName(0) == null) {
            return null;
        }
        return new NamesAndDocsForParameter() {
            @Override
            public @Nullable String getName() {
                return paramData.getName(0);
            }

            @Override
            public @Nullable String getJavadoc() {
                return null;
            }
        };
    }
}
