package net.neoforged.jst.accesstransformers;

import net.neoforged.jst.api.SourceTransformer;
import net.neoforged.jst.api.SourceTransformerPlugin;

/**
 * Access transformers allow modifying the access of classes, fields and methods.
 * Mods can use ATs to remove the final modifier, or increase the visibility of a method.
 */
public class AccessTransformersPlugin implements SourceTransformerPlugin {
    @Override
    public String getName() {
        return "accesstransformers";
    }

    @Override
    public SourceTransformer createTransformer() {
        return new AccessTransformersTransformer();
    }
}
