package net.neoforged.jst.api;

import com.intellij.psi.PsiFile;

public interface SourceTransformer {
    default void beforeRun() {
    }

    default void afterRun() {
    }

    void visitFile(PsiFile psiFile, Replacements replacements);
}
