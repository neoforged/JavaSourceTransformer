import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiJavaDocumentedElement;
import com.intellij.psi.PsiLambdaExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiRecursiveElementVisitor;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypes;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.SyntaxTraverser;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ObjectIntHashMap;
import namesanddocs.NamesAndDocsDatabase;
import namesanddocs.NamesAndDocsForClass;
import namesanddocs.NamesAndDocsForMethod;
import namesanddocs.NamesAndDocsForParameter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

class GatherReplacementsVisitor extends PsiRecursiveElementVisitor {

    /**
     * Key for attaching mapping data to a PsiClass. Used to prevent multiple lookups for the same class.
     */
    private static final Key<Optional<NamesAndDocsForClass>> CLASS_DATA_KEY = Key.create("names_and_docs_for_class");
    private static final Key<Optional<NamesAndDocsForMethod>> METHOD_DATA_KEY = Key.create("names_and_docs_for_method");

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
            var classData = getClassData(psiClass);
            if (classData != null) {
                applyJavadoc(psiClass, classData.getJavadoc(), replacements);
            }
        } else if (element instanceof PsiField psiField) {
            var classData = getClassData(psiField.getContainingClass());
            var fieldData = classData != null ? classData.getField(psiField.getName()) : null;
            if (fieldData != null) {
                // Add javadoc if available
                applyJavadoc(psiField, fieldData.getJavadoc(), replacements);
            }
        } else if (element instanceof PsiMethod psiMethod) {
            var methodData = getMethodData(psiMethod);
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
                    var jvmIndex = getJvmIndex(psiParameter, i);

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

    private int getJvmIndex(PsiParameter psiParameter, int index) {
        var declarationScope = psiParameter.getDeclarationScope();
        if (declarationScope instanceof PsiMethod psiMethod) {

            // Try to account for hidden parameters only present in bytecode since the
            // mapping data refers to parameters using those indices
            if (psiMethod.isConstructor() && psiMethod.getContainingClass() != null && psiMethod.getContainingClass().isEnum()) {
                index += 2;
            } else if (psiMethod.getContainingClass() != null && psiMethod.getContainingClass() != null
                    && !psiMethod.getContainingClass().hasModifierProperty(PsiModifier.STATIC)) {
                index += 1;
            }

            return index;
        } else if (declarationScope instanceof PsiLambdaExpression psiLambda) {
            // Naming lambdas doesn't really work
            return index;
        } else {
            return -1;
        }
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

    @SuppressWarnings("OptionalAssignedToNull")
    @Nullable
    private NamesAndDocsForClass getClassData(@Nullable PsiClass psiClass) {
        if (psiClass == null) {
            return null;
        }
        var classData = psiClass.getUserData(CLASS_DATA_KEY);
        if (classData != null) {
            return classData.orElse(null);
        } else {
            var sb = new StringBuilder();
            getBinaryClassName(psiClass, sb);
            if (sb.isEmpty()) {
                classData = Optional.empty();
            } else {
                classData = Optional.ofNullable(namesAndDocs.getClass(sb.toString()));
            }
            psiClass.putUserData(CLASS_DATA_KEY, classData);
            return classData.orElse(null);
        }
    }

    @SuppressWarnings("OptionalAssignedToNull")
    @Nullable
    private NamesAndDocsForMethod getMethodData(@Nullable PsiMethod psiMethod) {
        if (psiMethod == null) {
            return null;
        }
        var methodData = psiMethod.getUserData(METHOD_DATA_KEY);
        if (methodData != null) {
            return methodData.orElse(null);
        } else {
            methodData = Optional.empty();
            var classData = getClassData(psiMethod.getContainingClass());
            if (classData != null) {
                var methodName = psiMethod.getName();
                var methodSignature = getBinaryMethodSignature(psiMethod);
                methodData = Optional.ofNullable(classData.getMethod(methodName, methodSignature));
            }

            psiMethod.putUserData(METHOD_DATA_KEY, methodData);
            return methodData.orElse(null);
        }
    }

    public static String getBinaryMethodSignature(PsiMethod method) {
        StringBuilder signature = new StringBuilder();
        signature.append("(");
        for (PsiParameter param : method.getParameterList().getParameters()) {
            var binaryPresentation = ClassUtil.getBinaryPresentation(param.getType());
            if (binaryPresentation.isEmpty()) {
                System.err.println("Failed to create binary representation for type " + param.getType().getCanonicalText());
                binaryPresentation = "ERROR";
            }
            signature.append(binaryPresentation);
        }
        signature.append(")");
        var returnType = Optional.ofNullable(method.getReturnType()).orElse(PsiTypes.voidType());
        var returnTypeRepresentation = ClassUtil.getBinaryPresentation(returnType);
        if (returnTypeRepresentation.isEmpty()) {
            System.err.println("Failed to create binary representation for type " + returnType.getCanonicalText());
            returnTypeRepresentation = "ERROR";
        }
        signature.append(returnTypeRepresentation);
        return signature.toString();
    }


    /**
     * An adapted version of {@link ClassUtil#formatClassName(PsiClass, StringBuilder)} where Inner-Classes
     * use a $ separator while formatClassName separates InnerClasses with periods from their parent.
     */
    private static void getBinaryClassName(@NotNull final PsiClass aClass, @NotNull StringBuilder buf) {
        final String qName = ClassUtil.getJVMClassName(aClass);
        if (qName != null) {
            buf.append(qName.replace('.', '/'));
        } else {
            final PsiClass parentClass = PsiTreeUtil.getContextOfType(aClass, PsiClass.class, true);
            if (parentClass != null) {
                getBinaryClassName(parentClass, buf);
                buf.append("$");
                buf.append(getNonQualifiedClassIdx(aClass, parentClass));
                final String name = aClass.getName();
                if (name != null) {
                    buf.append(name);
                }
            }
        }
    }

    private static int getNonQualifiedClassIdx(@NotNull final PsiClass psiClass, @NotNull final PsiClass containingClass) {
        var indices =
                CachedValuesManager.getCachedValue(containingClass, () -> {
                    var map = new ObjectIntHashMap<PsiClass>();
                    int index = 0;
                    for (PsiClass aClass : SyntaxTraverser.psiTraverser().withRoot(containingClass).postOrderDfsTraversal().filter(PsiClass.class)) {
                        if (aClass.getQualifiedName() == null) {
                            map.put(aClass, ++index);
                        }
                    }
                    return CachedValueProvider.Result.create(map, containingClass);
                });

        return indices.get(psiClass);
    }

}
