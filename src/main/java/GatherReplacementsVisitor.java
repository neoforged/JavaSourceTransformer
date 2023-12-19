import com.intellij.openapi.util.TextRange;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiJavaDocumentedElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiRecursiveElementVisitor;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.ClassUtil;
import namesanddocs.NamesAndDocsDatabase;
import namesanddocs.NamesAndDocsForClass;
import namesanddocs.NamesAndDocsForParameter;
import org.jetbrains.annotations.NotNull;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

class GatherReplacementsVisitor extends PsiRecursiveElementVisitor {
    private final NamesAndDocsDatabase namesAndDocs;
    private final boolean enableJavadoc;
    private final List<Replacement> replacements;
    /**
     * Renamed parameters of the combined outer scopes we are currently visiting.
     * Since scopes may be nested (classes defined in method bodies and their methods),
     * this may contain the parameters of multiple scopes simultaneously.
     */
    private final Map<PsiParameter, NamesAndDocsForParameter> activeParameters = new IdentityHashMap<>();
    PsiClass currentPsiClass;
    NamesAndDocsForClass currentClass;

    public GatherReplacementsVisitor(NamesAndDocsDatabase namesAndDocs,
                                     boolean enableJavadoc,
                                     List<Replacement> replacements) {
        this.namesAndDocs = namesAndDocs;
        this.enableJavadoc = enableJavadoc;
        this.replacements = replacements;
    }

    @Override
    public void visitElement(@NotNull PsiElement element) {
        if (element instanceof PsiClass psiClass) {
            if (psiClass.getQualifiedName() != null) {
                var psiFacade = JavaPsiFacade.getInstance(element.getProject());
                var foundClass = psiFacade.findClass(psiClass.getQualifiedName(), GlobalSearchScope.everythingScope(element.getProject()));
                if (foundClass != psiClass) {
                    throw new IllegalStateException("DOUBLE LOAD");
                }
            }

            currentPsiClass = psiClass;
            var jvmClassName = ClassUtil.getJVMClassName(psiClass);
            if (jvmClassName == null) {
                // Anonymous class?
                currentClass = null;
            } else {
                var className = jvmClassName.replace('.', '/');
                currentClass = namesAndDocs.getClass(className);
            }

            if (currentClass == null) {
                return; // Skip classes without mapping data
            }

            // Add javadoc if available
            applyJavadoc(psiClass, currentClass.getJavadoc(), replacements);
        } else if (element instanceof PsiField psiField) {
            // sanity check
            if (psiField.getContainingClass() != currentPsiClass) {
                return;
            }

            var fieldData = currentClass.getField(psiField.getName());
            if (fieldData != null) {
                // Add javadoc if available
                applyJavadoc(psiField, fieldData.getJavadoc(), replacements);
            }
        } else if (element instanceof PsiMethod method) {
            // sanity check
            if (method.getContainingClass() != currentPsiClass) {
                return;
            }

            var methodSignature = ClassUtil.getAsmMethodSignature(method);

            var methodData = currentClass.getMethod(method.getName(), methodSignature);
            if (methodData != null) {
                // Add javadoc if available
                applyJavadoc(method, methodData.getJavadoc(), replacements);

                PsiParameter[] parameters = method.getParameterList().getParameters();
                boolean hadReplacements = false;
                for (int i = 0; i < parameters.length; i++) {
                    var psiParameter = parameters[i];
                    // We cannot replace parameters with no name, sadly
                    if (psiParameter.getNameIdentifier() == null) {
                        continue;
                    }
                    var paramData = methodData.getParameter(i);
                    if (paramData != null) {
                        // Replace parameters within the method body
                        activeParameters.put(psiParameter, paramData);

                        // Find and replace the parameter identifier
                        replacements.add(Replacement.create(
                                psiParameter.getNameIdentifier(), paramData.getName()
                        ));

                        hadReplacements = true;
                    }
                }

                // When replacements were made and activeParamets were added, we visit the method children here ourselves
                // and clean up active parameters afterward
                if (hadReplacements) {
                    try {
                        element.acceptChildren(this);
                    } finally {
                        for (var parameter : parameters) {
                            activeParameters.remove(parameter);
                        }
                    }
                    return;
                }
            }
        } else if (element instanceof PsiReferenceExpression refExpr && refExpr.getReferenceNameElement() != null) {
            for (var entry : activeParameters.entrySet()) {
                if (refExpr.isReferenceTo(entry.getKey())) {
                    replacements.add(Replacement.create(refExpr.getReferenceNameElement(), entry.getValue().getName()));
                    break;
                }
            }
        }

        element.acceptChildren(this);
    }

    private void applyJavadoc(PsiJavaDocumentedElement method, List<String> javadoc, List<Replacement> replacements) {
        if (!enableJavadoc) {
            return;
        }

        if (!javadoc.isEmpty()) {
            var existingDocComment = method.getDocComment();
            if (existingDocComment != null) {
                // replace right after
                var textRange = existingDocComment.getTextRange();
                replacements.add(
                        new Replacement(
                                new TextRange(textRange.getEndOffset(), textRange.getEndOffset()),
                                "/**\n"
                                        + javadoc.stream().map(line -> " * " + line + "\n")
                                        .collect(Collectors.joining())
                                        + " */\n"
                        )
                );
            } else {
                // Insert right before the method
                int startOffset;
                String indentText;
                if (method.getPrevSibling() != null && method.getPrevSibling() instanceof PsiWhiteSpace psiWhiteSpace) {
                    var lastNewline = psiWhiteSpace.getText().lastIndexOf('\n');
                    var wsRange = psiWhiteSpace.getTextRange();
                    // No newline, just take the entire whitespace as indent, and insert before
                    if (lastNewline == -1) {
                        indentText = " ".repeat(psiWhiteSpace.getTextLength());
                        startOffset = wsRange.getEndOffset();
                    } else {
                        // Otherwise we inherit the whitespace as our own indent
                        indentText = " ".repeat(psiWhiteSpace.getTextLength() - lastNewline - 1);
                        startOffset = wsRange.getEndOffset();
                    }
                } else {
                    indentText = "";
                    startOffset = method.getTextRange().getStartOffset();
                }
                replacements.add(
                        new Replacement(
                                new TextRange(startOffset, startOffset),
                                "/**\n"
                                        + javadoc.stream().map(line -> indentText + " * " + line + "\n")
                                        .collect(Collectors.joining())
                                        + indentText + " */\n" + indentText
                        )
                );
            }
        }
    }
}
