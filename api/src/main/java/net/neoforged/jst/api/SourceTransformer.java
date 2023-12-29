package net.neoforged.jst.api;

import com.intellij.psi.PsiFile;

public interface SourceTransformer {
    default void beforeRun(TransformContext context) {
    }

    default void afterRun(TransformContext context) {
    }

    void visitFile(PsiFile psiFile, Replacements replacements);
}

