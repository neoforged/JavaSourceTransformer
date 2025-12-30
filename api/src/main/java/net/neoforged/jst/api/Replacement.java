package net.neoforged.jst.api;

import com.intellij.openapi.util.TextRange;

import java.util.Comparator;

public record Replacement(TextRange range, String newText) {

    public static final Comparator<Replacement> COMPARATOR = Comparator
            .<Replacement>comparingInt(replacement -> replacement.range.getStartOffset())
            .thenComparingInt(replacement -> replacement.range.getEndOffset());
}
