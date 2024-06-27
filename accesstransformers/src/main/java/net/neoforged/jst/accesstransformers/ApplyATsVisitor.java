package net.neoforged.jst.accesstransformers;

import com.intellij.lang.jvm.JvmClassKind;
import com.intellij.navigation.NavigationItem;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiKeyword;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiRecordComponent;
import com.intellij.psi.PsiRecursiveElementVisitor;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.util.PsiClassUtil;
import net.neoforged.accesstransformer.parser.AccessTransformerFiles;
import net.neoforged.accesstransformer.parser.Target;
import net.neoforged.accesstransformer.parser.Transformation;
import net.neoforged.jst.api.Logger;
import net.neoforged.jst.api.PsiHelper;
import net.neoforged.jst.api.Replacements;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

class ApplyATsVisitor extends PsiRecursiveElementVisitor {
    private static final Set<String> ACCESS_MODIFIERS = Set.of(PsiModifier.PUBLIC, PsiModifier.PRIVATE, PsiModifier.PROTECTED);
    private static final Set<String> MODIFIERS = Set.of(PsiModifier.MODIFIERS);

    public static final EnumMap<Transformation.Modifier, String> MODIFIER_TO_STRING = new EnumMap<>(
            Map.of(Transformation.Modifier.PRIVATE, PsiModifier.PRIVATE, Transformation.Modifier.PUBLIC, PsiModifier.PUBLIC, Transformation.Modifier.PROTECTED, PsiModifier.PROTECTED)
    );
    public static final Map<String, Transformation.Modifier> STRING_TO_MODIFIER = MODIFIER_TO_STRING.entrySet()
            .stream().collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));

    private final AccessTransformerFiles ats;
    private final Replacements replacements;
    private final Map<Target, Transformation> pendingATs;
    private final Logger logger;
    boolean errored = false;

    public ApplyATsVisitor(AccessTransformerFiles ats, Replacements replacements, Map<Target, Transformation> pendingATs, Logger logger) {
        this.ats = ats;
        this.replacements = replacements;
        this.logger = logger;
        this.pendingATs = pendingATs;
    }

    @Override
    public void visitElement(@NotNull PsiElement element) {
        if (element instanceof PsiClass psiClass) {
            if (psiClass.getQualifiedName() != null) {
                String className = ClassUtil.getJVMClassName(psiClass);
                if (!ats.containsClassTarget(className)) {
                    // Skip this class and its children, but not the inner classes
                    for (PsiClass innerClass : psiClass.getInnerClasses()) {
                        visitElement(innerClass);
                    }
                    return;
                }

                var classAt = pendingATs.remove(new Target.ClassTarget(className));
                apply(classAt, psiClass, psiClass);
                // We also remove any possible inner class ATs declared for that class as all class targets targeting inner classes
                // generate a InnerClassTarget AT
                if (psiClass.getParent() instanceof PsiClass parent) {
                    pendingATs.remove(new Target.InnerClassTarget(ClassUtil.getJVMClassName(parent), className));
                }

                checkImplicitConstructor(psiClass, className, classAt);

                var fieldWildcard = pendingATs.remove(new Target.WildcardFieldTarget(className));
                if (fieldWildcard != null) {
                    for (PsiField field : psiClass.getFields()) {
                        // Apply a merged state if an explicit AT for the field already exists
                        var newState = merge(fieldWildcard, pendingATs.remove(new Target.FieldTarget(className, field.getName())));
                        logger.debug("Applying field wildcard AT %s to %s in %s", newState, field.getName(), className);
                        apply(newState, field, psiClass);
                    }
                }

                var methodWildcard = pendingATs.remove(new Target.WildcardMethodTarget(className));
                if (methodWildcard != null) {
                    for (PsiMethod method : psiClass.getMethods()) {
                        // Apply a merged state if an explicit AT for the method already exists
                        var newState = merge(methodWildcard, pendingATs.remove(method(className, method)));
                        logger.debug("Applying method wildcard AT %s to %s in %s", newState, method.getName(), className);
                        apply(newState, method, psiClass);
                    }
                }
            }
        } else if (element instanceof PsiField field) {
            final var cls = field.getContainingClass();
            if (cls != null && cls.getQualifiedName() != null) {
                String className = ClassUtil.getJVMClassName(cls);
                apply(pendingATs.remove(new Target.FieldTarget(className, field.getName())), field, cls);
            }
        } else if (element instanceof PsiMethod method) {
            final var cls = method.getContainingClass();
            if (cls != null && cls.getQualifiedName() != null) {
                String className = ClassUtil.getJVMClassName(cls);
                apply(pendingATs.remove(method(className, method)), method, cls);
            }
        }

        element.acceptChildren(this);
    }

    private void apply(@Nullable Transformation at, PsiModifierListOwner owner, PsiClass containingClass) {
        if (at == null) return;
        if (!at.isValid()) {
            error("Found invalid access transformer: %s. Final state: conflicting", at);
            return;
        }

        var targetInfo = new Object() {
            @Override
            public String toString() {
                if (owner instanceof PsiClass cls) {
                    return ClassUtil.getJVMClassName(cls);
                }
                String memberName;
                if (owner instanceof PsiMethod mtd && mtd.isConstructor()) {
                    memberName = "constructor";
                } else {
                    memberName = ((NavigationItem) owner).getName();
                }
                return memberName + " of " + ClassUtil.getJVMClassName(containingClass);
            }
        };
        logger.debug("Applying AT %s to %s", at, targetInfo);

        var modifiers = owner.getModifierList();

        var targetAcc = at.modifier();

        // If we're modifying a non-static interface method we can only make it public, meaning it must be defined as default
        if (containingClass.isInterface() && owner instanceof PsiMethod && !modifiers.hasModifierProperty(PsiModifier.STATIC)) {
            if (targetAcc != Transformation.Modifier.PUBLIC) {
                error("Access transformer %s targeting %s attempted to make a non-static interface method %s. They can only be made public.", at, targetInfo, targetAcc);
            } else {
                for (var kw : modifiers.getChildren()) {
                    if (kw instanceof PsiKeyword && kw.getText().equals(PsiKeyword.PRIVATE)) { // Strip private, replace it with default
                        replacements.replace(kw, PsiModifier.DEFAULT);
                        break;
                    }
                }
            }
        } else if (containingClass.isEnum() && owner instanceof PsiMethod mtd && mtd.isConstructor() && at.modifier().ordinal() < Transformation.Modifier.DEFAULT.ordinal()) {
            // Enum constructors can at best be package-private, any other attempt must be prevented
            error("Access transformer %s targeting %s attempted to make an enum constructor %s", at, targetInfo, at.modifier());
        } else if (targetAcc.ordinal() < detectModifier(modifiers, null).ordinal()) { // PUBLIC (0) < PROTECTED (1) < DEFAULT (2) < PRIVATE (3)
            modify(targetAcc, modifiers, Arrays.stream(modifiers.getChildren())
                    .filter(el -> el instanceof PsiKeyword)
                    .map(el -> (PsiKeyword) el)
                    .filter(kw -> ACCESS_MODIFIERS.contains(kw.getText()))
                    .findFirst());
        }

        var finalState = at.finalState();
        if (finalState == Transformation.FinalState.REMOVEFINAL && modifiers.hasModifierProperty(PsiModifier.FINAL)) {
            Arrays.stream(modifiers.getChildren())
                    .filter(el -> el instanceof PsiKeyword)
                    .map(el -> (PsiKeyword) el)
                    .filter(kw -> kw.getText().equals(PsiModifier.FINAL))
                    .findFirst()
                    .ifPresent(replacements::remove);
        } else if (finalState == Transformation.FinalState.MAKEFINAL && !modifiers.hasModifierProperty(PsiModifier.FINAL)) {
            error("Access transformer %s attempted to make %s final. Was non-final", at, targetInfo);
        }
    }

    private void modify(Transformation.Modifier targetAcc, PsiModifierList modifiers, Optional<PsiKeyword> existingModifier) {
        if (targetAcc == Transformation.Modifier.DEFAULT) {
            existingModifier.ifPresent(replacements::remove);
        } else {
            if (existingModifier.isPresent()) {
                replacements.replace(existingModifier.get(), MODIFIER_TO_STRING.get(targetAcc));
            } else {
                if (modifiers.getChildren().length == 0) {
                    // Empty modifiers are blank so we basically replace them
                    replacements.insertAfter(modifiers, MODIFIER_TO_STRING.get(targetAcc) + " ");
                } else {
                    var modifierStr = MODIFIER_TO_STRING.get(targetAcc);
                    Arrays.stream(modifiers.getChildren())
                            .filter(element -> element instanceof PsiKeyword && MODIFIERS.contains(element.getText()))
                            .findFirst()
                            // If there's other modifiers, insert just before the first
                            .ifPresentOrElse(first -> {
                                replacements.insertBefore(first, modifierStr + " ");
                            }, () -> {
                                // Otherwise insert before the declaration:
                                // - element type (interface, enum, class, record) in the case of classes
                                // - return type in the case of methods
                                // - identifier in the case of constructors
                                // - type in the case of fields
                                if (modifiers.getParent() instanceof PsiClass cls) {
                                    final String typeKeyword = detectKind(cls);

                                    PsiElement next = modifiers;
                                    while ((next = next.getNextSibling()) != null) {
                                        if (next instanceof PsiKeyword kw && kw.getText().equals(typeKeyword)) {
                                            replacements.insertBefore(kw, modifierStr + " ");
                                            break;
                                        }
                                    }
                                } else if (modifiers.getParent() instanceof PsiMethod method) {
                                    if (method.getReturnTypeElement() == null) {
                                        replacements.insertBefore(method.getNameIdentifier(), modifierStr + " ");
                                    } else {
                                        replacements.insertBefore(method.getReturnTypeElement(), modifierStr + " ");
                                    }
                                } else if (modifiers.getParent() instanceof PsiField field && field.getTypeElement() != null) {
                                    replacements.insertBefore(field.getTypeElement(), modifierStr + " ");
                                } else {
                                    // If all fails, insert before the other modifiers and move on
                                    replacements.insertBefore(modifiers, modifierStr + " ");
                                }
                            });
                }
            }
        }
    }

    /**
     * Handle the different behavior between applying ATs to source vs. bytecode.
     * In source, the implicit class constructor is not visible and its access level will automatically increase
     * when we change that of the containing class, while that is not true in production, where ATs are applied
     * to bytecode instead.
     * It also handles additional validation for record constructors, which have special rules.
     */
    private void checkImplicitConstructor(PsiClass psiClass, String className, @Nullable Transformation classAt) {
        if (psiClass.isRecord()) {
            StringBuilder descriptor = new StringBuilder("(");
            for (PsiRecordComponent recordComponent : psiClass.getRecordComponents()) {
                descriptor.append(ClassUtil.getBinaryPresentation(recordComponent.getType()));
            }
            descriptor.append(")V");
            var desc = descriptor.toString();

            var implicitAT = pendingATs.remove(new Target.MethodTarget(className, "<init>", desc));
            if (implicitAT != null && implicitAT.modifier() != detectModifier(psiClass.getModifierList(), classAt)) {
                error("Access transformer %s targeting the implicit constructor of %s is not valid, as a record's constructor must have the same access level as the record class. Please AT the record too: \"%s\"", implicitAT, className,
                        implicitAT.modifier().toString().toLowerCase(Locale.ROOT) + " " + className);
                pendingATs.remove(new Target.MethodTarget(className, "<init>", desc));
            } else if (classAt != null && detectModifier(psiClass.getModifierList(), null).ordinal() > classAt.modifier().ordinal() && implicitAT == null) {
                error("Access transformer %s targeting record class %s attempts to widen its access without widening the constructor's access. You must AT the constructor too: \"%s\"", classAt, className,
                        classAt.modifier().toString().toLowerCase(Locale.ROOT) + " " + className + " <init>" + desc);
                pendingATs.remove(new Target.MethodTarget(className, "<init>", desc));
            }
        } else if (psiClass.getClassKind() == JvmClassKind.CLASS) {
            // When widening the access of a class, we must take into consideration the fact that implicit constructors follow the access level of their owner
            if (psiClass.getConstructors().length == 0) {
                var constructorTarget = new Target.MethodTarget(className, "<init>", PsiHelper.getImplicitConstructorSignature(psiClass));
                var constructorAt = pendingATs.remove(constructorTarget);

                if (classAt != null && detectModifier(psiClass.getModifierList(), null).ordinal() > classAt.modifier().ordinal()) {
                    // If we cannot find an implicit constructor, we need to inject it if the AT doesn't match the expected constructor access
                    if (constructorAt == null || constructorAt.modifier() != classAt.modifier()) {
                        var expectedModifier = detectModifier(psiClass.getModifierList(), constructorAt);
                        injectConstructor(psiClass, className, expectedModifier);
                    }
                } else if (constructorAt != null && constructorAt.modifier().ordinal() < detectModifier(psiClass.getModifierList(), null).ordinal()) {
                    // If we're trying to widen the access of an undeclared implicit constructor, we must inject it
                    injectConstructor(psiClass, className, constructorAt.modifier());
                }
            }
        }
    }

    private void injectConstructor(PsiClass psiClass, String className, Transformation.Modifier modifier) {
        logger.debug("Injecting implicit constructor with access level %s into class %s", modifier, className);

        // Add 4 spaces of indent to indent the constructor inside the class
        int indent = 4;
        // If the class is preceded by whitespace, use the last line of that whitespace as the base indent
        if (psiClass.getPrevSibling() instanceof PsiWhiteSpace psiWhiteSpace) {
            indent += PsiHelper.getLastLineLength(psiWhiteSpace);
        }

        final String modifierString = modifier == Transformation.Modifier.DEFAULT ? "" : (MODIFIER_TO_STRING.get(modifier) + " ");
        // Inject the constructor after the opening brace, on a new line
        replacements.insertAfter(Objects.requireNonNull(psiClass.getLBrace()), "\n" +
                " ".repeat(indent) + modifierString + psiClass.getName() + "() {}");
    }

    private void error(String message, Object... args) {
        logger.error(message, args);
        errored = true;
    }

    private static String detectKind(PsiClass cls) {
        if (cls.isRecord()) {
            return "record";
        } else if (cls.isInterface()) {
            return "interface";
        } else if (cls.isEnum()) {
            return "enum";
        } else {
            return "class";
        }
    }

    private static Transformation merge(Transformation a, @Nullable Transformation b) {
        return b == null ? a : a.mergeStates(b);
    }

    private static Target.MethodTarget method(String owner, PsiMethod method) {
        return new Target.MethodTarget(owner, PsiHelper.getBinaryMethodName(method), PsiHelper.getBinaryMethodSignature(method));
    }

    private static Transformation.Modifier detectModifier(PsiModifierList owner, @Nullable Transformation trans) {
        if (trans != null) {
            return trans.modifier();
        }

        for (String mod : ACCESS_MODIFIERS) {
            if (owner.hasModifierProperty(mod)) {
                return STRING_TO_MODIFIER.get(mod);
            }
        }
        return Transformation.Modifier.DEFAULT;
    }
}
