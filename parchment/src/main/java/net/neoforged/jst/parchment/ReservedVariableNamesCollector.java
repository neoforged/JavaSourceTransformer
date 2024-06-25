package net.neoforged.jst.parchment;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiMethodReferenceExpression;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiRecursiveElementVisitor;
import com.intellij.psi.PsiReferenceExpression;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * An element visitor that collects all variable names that are in use, or can otherwise conflict if used as method parameter name.
 * <p>
 * A reserved variable name is:
 * <ul>
 *     <li>the name of a method parameter</li>
 *     <li>the identifier of a member used in an unqualified reference expression (e.g. <code>someField = "abc"</code>)</li>
 *     <li>the names of the fields an anonymous class or method-local class has</li>
 * </ul>
 */
public class ReservedVariableNamesCollector extends PsiRecursiveElementVisitor {
    public final Set<String> names;
    public ReservedVariableNamesCollector(Set<String> names) {
        this.names = names;
    }

    @Override
    public void visitElement(@NotNull PsiElement element) {
        if (element instanceof PsiParameter parameter) {
            names.add(parameter.getName());
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
                names.add(reference.getLastChild().getText());
            }
        } else if (element instanceof PsiLocalVariable variable) {
            names.add(variable.getName());
        } else if (element instanceof PsiClass cls) {
            // To catch cases where inherited protected fields of local classes take precedence over the parameters
            // when we encounter a class local to a method we will consider all its field names reserved
            names.addAll(PsiParchmentHelper.getAllFieldNames(cls));
            return; // But we don't need to process further as references in the methods declared in the local class are out of scope for this check
        }

        element.acceptChildren(this);
    }
}
