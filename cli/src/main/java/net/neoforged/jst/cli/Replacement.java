package net.neoforged.jst.cli;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;

import java.util.Comparator;

public record Replacement(TextRange range, String newText) {

    public static final Comparator<Replacement> COMPARATOR = Comparator.comparingInt(replacement -> replacement.range.getStartOffset());

    public static Replacement replace(PsiElement element, String newText) {
        return new Replacement(element.getTextRange(), newText);
    }

    public static Replacement insertBefore(PsiElement element, String newText) {
        var startOffset = element.getTextRange().getStartOffset();
        return new Replacement(new TextRange(
                startOffset,
                startOffset
        ), newText);
    }

    public static Replacement insertAfter(PsiElement element, String newText) {
        var endOffset = element.getTextRange().getEndOffset();
        return new Replacement(new TextRange(
                endOffset,
                endOffset
        ), newText);
    }
}
