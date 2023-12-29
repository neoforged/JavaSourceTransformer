package net.neoforged.jst.api;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;

import java.util.Comparator;

public record Replacement(TextRange range, String newText) {

    public static final Comparator<Replacement> COMPARATOR = Comparator.comparingInt(replacement -> replacement.range.getStartOffset());
}
