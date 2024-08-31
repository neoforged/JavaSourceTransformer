package net.neoforged.jst.api;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Replacements {
    private final List<Replacement> replacements;

    public Replacements(List<Replacement> replacements) {
        this.replacements = replacements;
    }
    
    public Replacements() {
        this(new ArrayList<>());
    }

    public boolean isEmpty() {
        return replacements.isEmpty();
    }

    public void replace(PsiElement element, String newText) {
        add(new Replacement(element.getTextRange(), newText));
    }

    public void remove(PsiElement element) {
        final int pos = element.getTextRange().getEndOffset();
        if (element.getContainingFile().getText().charAt(pos) == ' ') {
            add(new Replacement(new TextRange(
                    element.getTextRange().getStartOffset(),
                    pos + 1
            ), ""));
        } else {
            replace(element, "");
        }
    }

    public void insertBefore(PsiElement element, String newText) {
        var startOffset = element.getTextRange().getStartOffset();
        add(new Replacement(new TextRange(
                startOffset,
                startOffset
        ), newText));
    }

    public void insertAfter(PsiElement element, String newText) {
        var endOffset = element.getTextRange().getEndOffset();
        add(new Replacement(new TextRange(
                endOffset,
                endOffset
        ), newText));
    }

    public void add(Replacement replacement) {
        replacements.add(replacement);
    }

    public String apply(CharSequence originalContent) {
        // We will assemble the resulting file by iterating all ranges (replaced or not)
        // For this to work, the replacement ranges need to be in ascending order and non-overlapping
        replacements.sort(Replacement.COMPARATOR);

        var writer = new StringBuilder();
        // Copy up until the first replacement

        writer.append(originalContent, 0, replacements.get(0).range().getStartOffset());
        for (int i = 0; i < replacements.size(); i++) {
            var replacement = replacements.get(i);
            var range = replacement.range();
            if (i > 0) {
                // Copy between previous and current replacement verbatim
                var previousReplacement = replacements.get(i - 1);
                // validate that replacement ranges are non-overlapping
                if (previousReplacement.range().getEndOffset() > range.getStartOffset()) {
                    throw new IllegalStateException("Trying to replace overlapping ranges: "
                            + replacement + " and " + previousReplacement);
                }

                writer.append(
                        originalContent,
                        previousReplacement.range().getEndOffset(),
                        range.getStartOffset()
                );
            }
            writer.append(replacement.newText());
        }
        writer.append(originalContent, replacements.get(replacements.size() - 1).range().getEndOffset(), originalContent.length());
        return writer.toString();
    }
}
