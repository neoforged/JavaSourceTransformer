package net.neoforged.jst.unpick;

import net.neoforged.jst.api.SourceTransformer;
import net.neoforged.jst.api.SourceTransformerPlugin;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Experimental
public class UnpickPlugin implements SourceTransformerPlugin {
    @Override
    public String getName() {
        return "unpick";
    }

    @Override
    public SourceTransformer createTransformer() {
        return new UnpickTransformer();
    }
}
