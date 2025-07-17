package net.neoforged.jst.parchment;

import com.intellij.lang.jvm.JvmParameter;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiJavaDocumentedElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiRecursiveElementVisitor;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.search.GlobalSearchScope;
import net.neoforged.jst.api.PsiHelper;
import net.neoforged.jst.api.Replacements;
import net.neoforged.jst.parchment.namesanddocs.NamesAndDocsDatabase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.UnaryOperator;

class GatherReplacementsVisitor extends PsiRecursiveElementVisitor {
    private final NamesAndDocsDatabase namesAndDocs;
    private final boolean enableJavadoc;
    @Nullable
    private final UnaryOperator<String> conflictResolver;
    private final Replacements replacements;
    /**
     * Renamed parameters of the combined outer scopes we are currently visiting.
     * Since scopes may be nested (classes defined in method bodies and their methods),
     * this may contain the parameters of multiple scopes simultaneously.
     */
    private final Map<PsiParameter, String> activeParameters = new IdentityHashMap<>();
    /**
     * The parameter names that have already been used by a method in the outer scope and
     * therefore cannot be used in nested methods.
     */
    private final Set<String> activeNames = new HashSet<>();

    public GatherReplacementsVisitor(NamesAndDocsDatabase namesAndDocs,
                                     boolean enableJavadoc,
                                     @Nullable UnaryOperator<String> conflictResolver,
                                     Replacements replacements) {
        this.namesAndDocs = namesAndDocs;
        this.enableJavadoc = enableJavadoc;
        this.conflictResolver = conflictResolver;
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
                    throw new IllegalStateException("Class " + psiClass.getQualifiedName() + " was loaded from two different sources: " +
                                                    psiClass.getContainingFile().getVirtualFile().getPath() + " and " +
                                                    foundClass.getContainingFile().getVirtualFile().getPath());
                }
            }

            // Add javadoc if available
            var classData = PsiParchmentHelper.getClassData(namesAndDocs, psiClass);
            if (classData != null) {
                applyJavadoc(psiClass, classData.getJavadoc(), replacements);
            }
        } else if (element instanceof PsiField psiField) {
            var classData = PsiParchmentHelper.getClassData(namesAndDocs, psiField.getContainingClass());
            var fieldData = classData != null ? classData.getField(psiField.getName()) : null;
            if (fieldData != null) {
                // Add javadoc if available
                applyJavadoc(psiField, fieldData.getJavadoc(), replacements);
            }
        } else if (element instanceof PsiMethod psiMethod) {
            var methodData = PsiParchmentHelper.getMethodData(namesAndDocs, psiMethod);
            if (methodData != null) {

                Map<String, String> parameterJavadoc = new HashMap<>();
                Map<String, String> renamedParameters = new HashMap<>();

                final UnaryOperator<String> namer;
                if (conflictResolver == null || psiMethod.getBody() == null) {
                    namer = UnaryOperator.identity();
                } else {
                    final Set<String> localRefs = new HashSet<>();
                    // Existing parameter names are considered reserved to avoid patched-in parameters to conflict with Parchment names
                    for (JvmParameter parameter : psiMethod.getParameters()) {
                        localRefs.add(parameter.getName());
                    }
                    new ReservedVariableNamesCollector(localRefs).visitElement(psiMethod.getBody());
                    namer = p -> localRefs.contains(p) ? conflictResolver.apply(p) : p;
                }

                List<String> parameterOrder = new ArrayList<>();

                var parameters = psiMethod.getParameterList().getParameters();
                var parametersLvtIndices = PsiHelper.getParameterLvtIndices(psiMethod);
                boolean hadReplacements = false;

                int parameterOffset = 0;
                if (psiMethod.isConstructor()) {
                    // Enums offset the parameter index by 2 since they add an int and a string param which are not visible in the source
                    if (psiMethod.getContainingClass().isEnum()) {
                        parameterOffset = 2;
                    }
                    // Non-static inner classes capture their owner as the first parameter
                    else if (PsiHelper.isNonStaticInnerClass(psiMethod.getContainingClass())) {
                        parameterOffset = 1;
                    }
                }

                for (int i = 0; i < parameters.length; i++) {
                    var psiParameter = parameters[i];
                    // We cannot replace parameters with no name, sadly
                    if (psiParameter.getNameIdentifier() == null) {
                        continue;
                    }

                    // Parchment stores parameter indices based on the index of the parameter in the actual compiled method
                    // to account for synthetic parameter not found in the source-code, we must adjust the index accordingly.
                    var jvmIndex = parametersLvtIndices[i];

                    var paramData = methodData.getParameter(parameterOffset + i, jvmIndex);
                    // Optionally replace the parameter name, but skip record constructors, since those could have
                    // implications for the field names.
                    if (paramData != null && paramData.getName() != null && !PsiHelper.isRecordConstructor(psiMethod)) {
                        var paramName = namer.apply(paramData.getName());

                        // We cannot rename a parameter to name that was already taken in this scope
                        if (activeNames.contains(paramName)) {
                            // If we have no conflict resolver then we simply don't try to rename this parameter
                            if (conflictResolver == null) {
                                parameterOrder.add(psiParameter.getName());
                                continue;
                            }

                            // Keep applying the conflict resolver until the name is no longer used
                            while (activeNames.contains(paramName)) {
                                paramName = conflictResolver.apply(paramName);
                            }
                        }

                        // Replace parameters within the method body
                        activeParameters.put(psiParameter, paramName);
                        activeNames.add(paramName);

                        // Find and replace the parameter identifier
                        replacements.replace(psiParameter.getNameIdentifier(), paramName);

                        // Record the replacement for remapping existing Javadoc @param tags
                        renamedParameters.put(psiParameter.getName(), paramName);

                        hadReplacements = true;

                        parameterOrder.add(paramName);
                    } else {
                        parameterOrder.add(psiParameter.getName());
                    }

                    // Optionally provide parameter javadocs
                    if (paramData != null && paramData.getJavadoc() != null) {
                        parameterJavadoc.put(
                                Objects.requireNonNullElse(paramData.getName(), psiParameter.getName()),
                                paramData.getJavadoc()
                        );
                    }
                }

                // Add javadoc if available
                if (enableJavadoc) {
                    JavadocHelper.enrichJavadoc(
                            psiMethod,
                            methodData.getJavadoc(),
                            parameterJavadoc,
                            renamedParameters,
                            parameterOrder,
                            replacements
                    );
                }

                // When replacements were made and activeParameters were added, we visit the method children here ourselves
                // and clean up active parameters afterward
                if (hadReplacements) {
                    try {
                        element.acceptChildren(this);
                    } finally {
                        for (var parameter : parameters) {
                            var nm = activeParameters.remove(parameter);
                            if (nm != null) {
                                activeNames.remove(nm);
                            }
                        }
                    }
                    return;
                }
            }
        } else if (element instanceof PsiReferenceExpression refExpr && refExpr.getReferenceNameElement() != null) {
            for (var entry : activeParameters.entrySet()) {
                if (refExpr.isReferenceTo(entry.getKey())) {
                    replacements.replace(refExpr.getReferenceNameElement(), entry.getValue());
                    break;
                }
            }
        }

        element.acceptChildren(this);
    }

    private void applyJavadoc(PsiJavaDocumentedElement psiElement,
                              List<String> javadoc,
                              Replacements replacements) {
        if (enableJavadoc && !javadoc.isEmpty()) {
            JavadocHelper.enrichJavadoc(psiElement, javadoc, replacements);
        }
    }
}
