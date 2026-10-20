package net.neoforged.jst.genericswidening;

import net.neoforged.jst.api.SourceTransformer;
import net.neoforged.jst.api.SourceTransformerPlugin;

public final class GenericsWideningPlugin implements SourceTransformerPlugin {
    @Override
    public String getName() {
        return "generics-widening";
    }

    @Override
    public SourceTransformer createTransformer() {
        return new GenericsWideningTransformer();
    }
}
