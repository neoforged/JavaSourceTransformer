package net.neoforged.jst.interfaceinjection;

import net.neoforged.jst.api.SourceTransformer;
import net.neoforged.jst.api.SourceTransformerPlugin;

/**
 * Plugin that injects stub interfaces to classes.
 * <p>
 * Mods can use interface injection to have compile-time access to the interfaces they add to classes via Mixins.
 */
public class InterfaceInjectionPlugin implements SourceTransformerPlugin {
    @Override
    public String getName() {
        return "interface-injection";
    }

    @Override
    public SourceTransformer createTransformer() {
        return new InterfaceInjectionTransformer();
    }
}
