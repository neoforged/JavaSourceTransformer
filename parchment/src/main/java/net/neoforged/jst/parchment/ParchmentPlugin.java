package net.neoforged.jst.parchment;

import net.neoforged.jst.api.SourceTransformer;
import net.neoforged.jst.api.SourceTransformerPlugin;

public class ParchmentPlugin implements SourceTransformerPlugin {
    @Override
    public String getName() {
        return "parchment";
    }

    @Override
    public SourceTransformer createTransformer() {
        return new ParchmentTransformer();
    }
}
