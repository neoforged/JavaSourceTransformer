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
import namesanddocs.NamesAndDocsDatabase;
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
            // This is a sanity check to ensure classes we process are actually findable via the facade and resolve to the same class
            // If they don't, it means either references may be broken or we're loading classes twice.
            if (psiClass.getQualifiedName() != null) {
                var psiFacade = JavaPsiFacade.getInstance(element.getProject());
                var foundClass = psiFacade.findClass(psiClass.getQualifiedName(), GlobalSearchScope.everythingScope(element.getProject()));
                if (foundClass == null) {
                    throw new IllegalStateException("Failed to find how class " + psiClass.getQualifiedName() + " was loaded while processing it");
                } else if (foundClass != psiClass) {
                    throw new IllegalStateException("Class " + psiClass + " was loaded from two different sources");
                }
            }

            // Add javadoc if available
            var classData = PsiHelper.getClassData(namesAndDocs, psiClass);
            if (classData != null) {
                applyJavadoc(psiClass, classData.getJavadoc(), replacements);
            }
        } else if (element instanceof PsiField psiField) {
            var classData = PsiHelper.getClassData(namesAndDocs, psiField.getContainingClass());
            var fieldData = classData != null ? classData.getField(psiField.getName()) : null;
            if (fieldData != null) {
                // Add javadoc if available
                applyJavadoc(psiField, fieldData.getJavadoc(), replacements);
            }
        } else if (element instanceof PsiMethod psiMethod) {
            var methodData = PsiHelper.getMethodData(namesAndDocs, psiMethod);
            if (methodData != null) {
                // Add javadoc if available
                applyJavadoc(psiMethod, methodData.getJavadoc(), replacements);

                PsiParameter[] parameters = psiMethod.getParameterList().getParameters();
                boolean hadReplacements = false;
                for (int i = 0; i < parameters.length; i++) {
                    var psiParameter = parameters[i];
                    // We cannot replace parameters with no name, sadly
                    if (psiParameter.getNameIdentifier() == null) {
                        continue;
                    }

                    // Parchment stores parameter indices based on the index of the parameter in the actual compiled method
                    // to account for synthetic parameter not found in the source-code, we must adjust the index accordingly.
                    var jvmIndex = PsiHelper.getBinaryIndex(psiParameter, i);

                    var paramData = methodData.getParameter(jvmIndex);
                    if (paramData != null && paramData.getName() != null) {
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
