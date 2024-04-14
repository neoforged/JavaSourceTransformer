package net.neoforged.jst.accesstransformers;

import net.neoforged.jst.api.SourceTransformer;
import net.neoforged.jst.api.SourceTransformerPlugin;

public class ATsPlugin implements SourceTransformerPlugin {
    @Override
    public String getName() {
        return "accesstransformers";
    }

    @Override
    public SourceTransformer createTransformer() {
        return new ATsTransformer();
    }
}
