package net.neoforged.jst.parchment;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiMethodReferenceExpression;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiRecursiveElementVisitor;
import com.intellij.psi.PsiReferenceExpression;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public class LocalReferenceCollector extends PsiRecursiveElementVisitor {
    public final Set<String> references;
    public LocalReferenceCollector(Set<String> references) {
        this.references = references;
    }

    @Override
    public void visitElement(@NotNull PsiElement element) {
        if (element instanceof PsiParameter parameter) {
            references.add(parameter.getName());
        } else if (element instanceof PsiReferenceExpression reference && !(element instanceof PsiMethodReferenceExpression)) {
            boolean qualified = false;
            // Unqualified references are to be considered local variables
            for (PsiElement child : reference.getChildren()) {
                if (child instanceof PsiJavaToken token && token.getTokenType().getIndex() == 80) { // DOT
                    qualified = true;
                    break;
                }
            }

            if (!qualified) {
                references.add(reference.getLastChild().getText());
            }
        } else if (element instanceof PsiLocalVariable variable) {
            references.add(variable.getName());
        }

        element.acceptChildren(this);
    }
}
