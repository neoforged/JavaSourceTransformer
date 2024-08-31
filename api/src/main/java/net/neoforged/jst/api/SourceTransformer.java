package net.neoforged.jst.api;

import com.intellij.psi.PsiFile;

import java.util.List;

/**
 * Transformers are created through {@link SourceTransformerPlugin plugins}, and handle source replacements.
 * <p>
 * Transformers will be given to picocli for option collection, so they can accept CLI parameters.
 * It is <b>strongly recommended</b> that transformers prefix their options with the transformer name.
 */
public interface SourceTransformer {
    /**
     * Invoked before source files are visited for transformation.
     * <p>
     * Can be used for loading data from CLI parameters.
     *
     * @param context the transform context
     */
    default void beforeRun(TransformContext context) {
    }

    /**
     * Invoke after replacements are collected for a given file, but before they are applied.
     * <p>
     * Can be used to react to or verify the replacements that were collected.
     * @param fileEntry the file entry being transformed
     * @param replacements the replacements that were collected; read-only
     * @return {@code true} if the transformation should continue, {@code false} otherwise
     */
    default boolean beforeReplacement(FileEntry fileEntry, List<Replacement> replacements) {
        return true;
    }

    /**
     * Invoked after all source transformations are finished.
     * <p>
     * Can be used for post-transformation validation.
     *
     * @param context the transform context
     * @return {@code true} if the transformation was successful, {@code false} otherwise
     */
    default boolean afterRun(TransformContext context) {
        return true;
    }

    /**
     * Visit the given {@code psiFile} for transformation.
     *
     * @param psiFile      the file being transformed
     * @param replacements the replacement collector, used to replace the value of psi tree elements
     */
    void visitFile(PsiFile psiFile, Replacements replacements);
}

