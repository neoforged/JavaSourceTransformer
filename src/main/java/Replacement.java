import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;

public record Replacement(TextRange range, String newText) {

    public static final Comparator<Replacement> COMPARATOR = Comparator.comparingInt(replacement -> replacement.range.getStartOffset());

    public static Replacement create(PsiElement element, String newText) {
        return new Replacement(element.getTextRange(), newText);
    }
}
