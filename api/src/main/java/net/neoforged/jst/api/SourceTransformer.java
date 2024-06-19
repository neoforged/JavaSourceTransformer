package net.neoforged.jst.api;

import com.intellij.psi.PsiFile;

public interface SourceTransformer {
    default void beforeRun(TransformContext context) {
    }

    default boolean afterRun(TransformContext context) {
        return true;
    }

    void visitFile(PsiFile psiFile, Replacements replacements);
}

