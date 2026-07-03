package net.neoforged.jst.enumextensions;

import net.neoforged.jst.api.SourceTransformer;
import net.neoforged.jst.api.SourceTransformerPlugin;

public class EnumExtensionPlugin implements SourceTransformerPlugin {
    @Override
    public String getName() {
        return "enum-extensions";
    }

    @Override
    public SourceTransformer createTransformer() {
        return new EnumExtensionTransformer();
    }
}
