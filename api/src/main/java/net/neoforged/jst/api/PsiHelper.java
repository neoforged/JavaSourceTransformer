package net.neoforged.jst.api;

import com.intellij.lang.jvm.types.JvmPrimitiveTypeKind;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterListOwner;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.PsiTypes;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.SyntaxTraverser;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ObjectIntHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class PsiHelper {
    public static String getBinaryMethodName(PsiMethod psiMethod) {
        return psiMethod.isConstructor() ? "<init>" : psiMethod.getName();
    }

    public static Iterator<String> getOverloadedSignatures(PsiMethod method) {
        final List<String> parameters = new ArrayList<>();
        final var returnType = Optional.ofNullable(method.getReturnType()).orElse(PsiTypes.voidType());
        String returnTypeRepresentation = ClassUtil.getBinaryPresentation(returnType);
        if (returnTypeRepresentation.isEmpty()) {
            System.err.println("Failed to create binary representation for type " + returnType.getCanonicalText());
            returnTypeRepresentation = "ERROR";
        }
        final String retRep = returnTypeRepresentation;

        // Add implicit constructor parameters
        // Private enumeration constructors have two hidden parameters (enun name+ordinal)
        if (isEnumConstructor(method)) {
            parameters.add("Ljava/lang/String;I");
        }
        // Non-Static inner class constructors have the enclosing class as their first argument
        else if (isNonStaticInnerClassConstructor(method)) {
            var parent = Objects.requireNonNull(Objects.requireNonNull(method.getContainingClass()).getContainingClass());
            final StringBuilder par = new StringBuilder();
            par.append("L");
            getBinaryClassName(parent, par);
            par.append(";");
            parameters.add(par.toString());
        }

        for (PsiParameter param : method.getParameterList().getParameters()) {
            var binaryPresentation = ClassUtil.getBinaryPresentation(param.getType());
            if (binaryPresentation.isEmpty()) {
                System.err.println("Failed to create binary representation for type " + param.getType().getCanonicalText());
                binaryPresentation = "ERROR";
            }
            parameters.add(binaryPresentation);
        }

        return new Iterator<>() {
            @Override
            public boolean hasNext() {
                return !parameters.isEmpty();
            }

            @Override
            public String next() {
                StringBuilder signature = new StringBuilder();
                signature.append("(");
                parameters.forEach(signature::append);
                signature.append(")").append(retRep);
                parameters.remove(parameters.size() - 1);
                return signature.toString();
            }
        };
    }

    public static String getImplicitConstructorSignature(PsiClass psiClass) {
        StringBuilder signature = new StringBuilder();
        signature.append("(");
        // Non-Static inner class constructors have the enclosing class as their first argument
        if (isNonStaticInnerClass(psiClass)) {
            var parent = Objects.requireNonNull(Objects.requireNonNull(psiClass.getContainingClass()));
            signature.append("L");
            getBinaryClassName(parent, signature);
            signature.append(";");
        }
        signature.append(")V");
        return signature.toString();
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
                    for (var aClass : SyntaxTraverser.psiTraverser().withRoot(containingClass).postOrderDfsTraversal().filter(PsiClass.class)) {
                        // We're only interested in actual classes without qualified names (type parameters are an instance of PsiClass)
                        if (!(aClass instanceof PsiTypeParameter) && aClass.getQualifiedName() == null) {
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
            return containingClass != null && isNonStaticInnerClass(containingClass);
        }
        return false;
    }

    public static boolean isNonStaticInnerClass(PsiClass psiClass) {
        return psiClass.getContainingClass() != null && !psiClass.hasModifierProperty(PsiModifier.STATIC);
    }

    /**
     * Gets the local variable table indices of the parameters for the given method
     * or lambda expression
     */
    public static int[] getParameterLvtIndices(PsiParameterListOwner methodOrLambda) {

        // Account for hidden parameters before the first actual parameter
        int currentIndex = 0;
        if (methodOrLambda instanceof PsiMethod psiMethod) {
            if (!psiMethod.hasModifierProperty(PsiModifier.STATIC)) {
                currentIndex++; // this pointer
            }

            // Try to account for hidden parameters only present in bytecode since the
            // mapping data refers to parameters using those indices
            if (isEnumConstructor(psiMethod)) {
                currentIndex += 2;
            } else if (isNonStaticInnerClassConstructor(psiMethod)) {
                currentIndex += 1;
            }
        }

        var parameters = methodOrLambda.getParameterList().getParameters();
        var lvti = new int[parameters.length];
        for (int i = 0; i < lvti.length; i++) {
            lvti[i] = currentIndex++;
            // double and long use 2 slots in the LVT
            if (parameters[i].getType() instanceof PsiPrimitiveType primitiveType) {
                var kind = primitiveType.getKind();
                if (kind == JvmPrimitiveTypeKind.LONG || kind == JvmPrimitiveTypeKind.DOUBLE) {
                    currentIndex++;
                }
            }
        }

        return lvti;

    }

    public static boolean isRecordConstructor(PsiMethod psiMethod) {
        var containingClass = psiMethod.getContainingClass();
        return containingClass != null && containingClass.isRecord() && psiMethod.isConstructor();
    }

    public static int getLastLineLength(PsiWhiteSpace psiWhiteSpace) {
        var lastNewline = psiWhiteSpace.getText().lastIndexOf('\n');
        if (lastNewline != -1) {
            return psiWhiteSpace.getTextLength() - lastNewline - 1;
        } else {
            return psiWhiteSpace.getTextLength();
        }
    }

    @Nullable
    public static PsiElement resolve(PsiReferenceExpression expression) {
        try {
            return expression.resolve();
        } catch (Exception ignored) {
            return null;
        }
    }
}
