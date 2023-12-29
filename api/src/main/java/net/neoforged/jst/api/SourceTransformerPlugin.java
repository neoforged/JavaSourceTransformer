package net.neoforged.jst.api;

/**
 * Accessed via {@link java.util.ServiceLoader}.
 */
public interface SourceTransformerPlugin {

    /**
     * Unique name used in command-line options to enable this plugin.
     */
    String getName();

    /**
     * Creates a new transformer to be applied to source code.
     */
    SourceTransformer createTransformer();

}

