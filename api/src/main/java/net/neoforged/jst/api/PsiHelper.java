package net.neoforged.jst.api;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiLambdaExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiTypes;
import com.intellij.psi.SyntaxTraverser;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ObjectIntHashMap;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.Optional;

public final class PsiHelper {
    public static String getBinaryMethodName(PsiMethod psiMethod) {
        return psiMethod.isConstructor() ? "<init>" : psiMethod.getName();
    }

    public static String getBinaryMethodSignature(PsiMethod method) {
        StringBuilder signature = new StringBuilder();
        signature.append("(");
        // Add implicit constructor parameters
        // Private enumeration constructors have two hidden parameters (enun name+ordinal)
        if (isEnumConstructor(method)) {
            signature.append("Ljava/lang/String;I");
        }
        // Non-Static inner class constructors have the enclosing class as their first argument
        else if (isNonStaticInnerClassConstructor(method)) {
            var parent = Objects.requireNonNull(Objects.requireNonNull(method.getContainingClass()).getContainingClass());
            signature.append("L");
            getBinaryClassName(parent, signature);
            signature.append(";");
        }

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
    public static void getBinaryClassName(@NotNull final PsiClass aClass, @NotNull StringBuilder buf) {
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

    private static boolean isEnumConstructor(PsiMethod method) {
        if (method.isConstructor()) {
            var containingClass = method.getContainingClass();
            return containingClass != null && containingClass.isEnum();
        }
        return false;
    }

    private static boolean isNonStaticInnerClassConstructor(PsiMethod method) {
        if (method.isConstructor()) {
            var containingClass = method.getContainingClass();
            return containingClass != null
                    && containingClass.getContainingClass() != null
                    && !containingClass.hasModifierProperty(PsiModifier.STATIC);
        }
        return false;
    }

    public static int getBinaryIndex(PsiParameter psiParameter, int index) {
        var declarationScope = psiParameter.getDeclarationScope();
        if (declarationScope instanceof PsiMethod psiMethod) {
            if (!psiMethod.hasModifierProperty(PsiModifier.STATIC)) {
                index++; // this pointer
            }

            // Try to account for hidden parameters only present in bytecode since the
            // mapping data refers to parameters using those indices
            if (isEnumConstructor(psiMethod)) {
                index += 2;
            } else if (isNonStaticInnerClassConstructor(psiMethod)) {
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
}
