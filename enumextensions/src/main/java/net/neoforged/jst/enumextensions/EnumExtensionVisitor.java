package net.neoforged.jst.enumextensions;

import com.intellij.lang.jvm.JvmModifier;
import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiEnumConstant;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiRecursiveElementVisitor;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeVisitor;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.containers.MultiMap;
import net.neoforged.jst.api.ImportHelper;
import net.neoforged.jst.api.PsiHelper;
import net.neoforged.jst.api.Replacements;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

class EnumExtensionVisitor extends PsiRecursiveElementVisitor {
    private final Replacements replacements;
    private final MultiMap<String, ExtensionPrototype> extensions;
    private final StubStore stubs;

    @Nullable
    private final String marker;
    
    @Nullable
    private final String requiredInterface;

    EnumExtensionVisitor(Replacements replacements, MultiMap<String, ExtensionPrototype> extensions, StubStore stubs, @Nullable String marker, @Nullable String requiredInterface) {
        this.replacements = replacements;
        this.extensions = extensions;
        this.stubs = stubs;
        this.marker = marker;
        this.requiredInterface = requiredInterface;
    }

    @Override
    public void visitElement(@NotNull PsiElement element) {
        if (element instanceof PsiClass psiClass) {
            if (psiClass.getQualifiedName() == null) {
                return;
            }

            String className = ClassUtil.getJVMClassName(psiClass);
            inject(psiClass, extensions.get(className.replace('.', '/')));

            for (PsiClass innerClass : psiClass.getInnerClasses()) {
                visitElement(innerClass);
            }
        }
    }

    @Override
    public void visitFile(@NotNull PsiFile file) {
        file.acceptChildren(this);
    }

    private void inject(PsiClass psiClass, Collection<ExtensionPrototype> targets) {
        // We cannot enum-extend things that aren't enums
        if (targets.isEmpty() || !psiClass.isEnum()) {
            return;
        }
        
        if (psiClass.hasModifier(JvmModifier.ABSTRACT)) {
            throw new IllegalArgumentException("Cannot extend abstract enum " + psiClass.getQualifiedName());
        }
        
        if (requiredInterface != null) {
            // We check the implements list too in case the required interface isn't present on the classpath
            if (Stream.concat(
                    Arrays.stream(psiClass.getInterfaces()).map(PsiClass::getQualifiedName),
                    Arrays.stream(psiClass.getImplementsList().getReferenceElements()).map(PsiJavaCodeReferenceElement::getQualifiedName)
            ).noneMatch(requiredInterface::equals)) {
                throw new IllegalArgumentException("Enum " + psiClass.getQualifiedName() + " must implement " + requiredInterface + " to be extended");
            }
        }

        var imports = ImportHelper.get(psiClass.getContainingFile());
        
        var fields = psiClass.getFields();
        AtomicBoolean insertingAfterConstant = new AtomicBoolean(false);
        var toInsertAfter = IntStream.range(0, fields.length)
                .mapToObj(i -> fields[fields.length - (1 + i)])
                .filter(f -> f instanceof PsiEnumConstant)
                // If there's args, we want to insert after the entire enum entry, not just the constant name
                .map(f -> ((PsiEnumConstant)f).getArgumentList() instanceof PsiElement args ? args : f)
                .findFirst()
                .map(e -> {
                    insertingAfterConstant.set(true);
                    return e;
                })
                .orElse(Objects.requireNonNull(psiClass.getLBrace())); // If there's no existing enum entries, insert after the opening brace

        // Add 4 spaces of indent to indent the enum entry inside the class
        int indent;
        // If the class is preceded by whitespace, use the last line of that whitespace as the base indent
        if (psiClass.getPrevSibling() instanceof PsiWhiteSpace psiWhiteSpace) {
            indent = 4 + PsiHelper.getLastLineLength(psiWhiteSpace);
        } else {
            indent = 4;
        }
        
        replacements.insertAfter(
                toInsertAfter,
                (insertingAfterConstant.get() ? "," : "") + targets.stream()
                        .sorted(Comparator.comparing(ExtensionPrototype::name))
                        .map(extension -> {
                            PsiMethod ctor = null;
                            for (var ctorCandidate : psiClass.getConstructors()) {
                                var descriptor = ClassUtil.getAsmMethodSignature(ctorCandidate);
                                if (extension.ctorDescriptor().equals(descriptor)) {
                                    ctor = ctorCandidate;
                                    break;
                                }
                            }
                            var entry = new StringBuilder();
                            entry.append("\n").append(" ".repeat(indent)).append(decorate(imports, extension.name()));
                            if (ctor != null && ctor.getParameterList().getParametersCount() > 0) {
                                entry.append('(');
                                switch (extension.parameters()) {
                                    case ExtensionPrototype.EnumParameters.Constant(var params) -> {
                                        if (params.size() != ctor.getParameterList().getParametersCount()) {
                                            throw new IllegalArgumentException("Parameter count mismatch for extension " + extension.name() + ": expected " + ctor.getParameterList().getParametersCount() + " but got " + params.size());
                                        }
                                        for (var param : params) {
                                            switch (param) {
                                                case String s -> entry.append('"').append(escape(s)).append('"');
                                                case Character c -> entry.append('\'').append(escape(c.toString())).append('\'');
                                                case null, default -> entry.append(param);
                                            }
                                            entry.append(", ");
                                        }
                                        if (!params.isEmpty()) {
                                            entry.setLength(entry.length() - 2); // Remove trailing comma and space
                                        }
                                    }
                                    case ExtensionPrototype.EnumParameters.FieldReference(var owner, var fieldName) -> {
                                        var className = possiblyImport(imports, owner, fieldName, false);
                                        for (int i = 0; i < ctor.getParameterList().getParametersCount(); i++) {
                                            if (i > 0) {
                                                entry.append(", ");
                                            }
                                            var parameterType = TypeConversionUtil.erasure(
                                                    ctor.getParameterList().getParameters()[i].getType()
                                            );
                                            String typeText = getTypeText(parameterType, imports);
                                            entry.append("(").append(typeText).append(") ").append(className).append('.').append(fieldName).append(".getParameter(").append(i).append(')');
                                        }
                                    }
                                    case ExtensionPrototype.EnumParameters.MethodReference(var owner, var methodName) -> {
                                        var className = possiblyImport(imports, owner, methodName, true);
                                        for (int i = 0; i < ctor.getParameterList().getParametersCount(); i++) {
                                            if (i > 0) {
                                                entry.append(", ");
                                            }
                                            var parameterType = TypeConversionUtil.erasure(
                                                    ctor.getParameterList().getParameters()[i].getType()
                                            );
                                            String typeText = getTypeText(parameterType, imports);
                                            entry.append("(").append(typeText).append(") ").append(className).append('.').append(methodName).append("(").append(i).append(", ").append(typeText).append(".class)");
                                        }
                                    }
                                }
                                entry.append(')');
                            }
                            return entry.toString();
                        })
                        .collect(Collectors.joining(","))
        );
    }

    private String getTypeText(PsiType parameterType, ImportHelper imports) {
        return parameterType.accept(new PsiTypeVisitor<>() {
            @Override
            public String visitPrimitiveType(@NotNull PsiPrimitiveType primitiveType) {
                return primitiveType.getCanonicalText();
            }

            @Override
            public String visitClassType(@NotNull PsiClassType classType) {
                PsiClass aClass = classType.resolve();
                if (aClass == null) {
                    throw new IllegalArgumentException("Cannot find fully qualified name for type: " + classType.getCanonicalText());
                }
                return possiblyImport(imports, aClass.getQualifiedName());
            }

            @Override
            public String visitArrayType(@NotNull PsiArrayType arrayType) {
                return arrayType.getComponentType().accept(this) + "[]";
            }
        });
    }

    private static String escape(String s){
        // Every unicode character except LF, CR, \, or " should be valid within a Java string literal
        // See https://docs.oracle.com/javase/specs/jls/se25/html/jls-3.html#jls-3.10.5
        return s.replace("\\", "\\\\")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\'", "\\'") // Since we'll run characters through this too, and it works fine
                .replace("\"", "\\\"");
    }

    private String possiblyImport(@Nullable ImportHelper helper, String fqn) {
        return helper == null ? fqn : helper.importClass(fqn);
    }

    private String possiblyImport(@Nullable ImportHelper helper, String toImport, String member, boolean isMethod) {
        var fqn = stubs.createStub(toImport, member, isMethod);
        return helper == null ? fqn : helper.importClass(fqn);
    }

    private String decorate(@Nullable ImportHelper helper, String entry) {
        if (marker == null) {
            return entry;
        }
        return "@" + (helper == null ? marker : helper.importClass(marker)) + " " + entry;
    }
}
