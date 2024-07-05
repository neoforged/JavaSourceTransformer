package net.neoforged.jst.interfaceinjection;

import net.neoforged.jst.api.SourceTransformer;
import net.neoforged.jst.api.SourceTransformerPlugin;

/**
 * Plugin that adds implements/extends clauses for interfaces to classes in a data-driven fashion, and creates stubs for these interfaces
 * to be able to still compile the modified code without access to the actual interface definitions.
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
