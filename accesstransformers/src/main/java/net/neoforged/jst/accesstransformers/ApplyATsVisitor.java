package net.neoforged.jst.accesstransformers;

import com.intellij.navigation.NavigationItem;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiKeyword;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiRecursiveElementVisitor;
import com.intellij.psi.util.ClassUtil;
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
import java.util.Map;
import java.util.Optional;
import java.util.Set;

class ApplyATsVisitor extends PsiRecursiveElementVisitor {
    private static final Set<String> ACCESS_MODIFIERS = Set.of(PsiModifier.PUBLIC, PsiModifier.PRIVATE, PsiModifier.PROTECTED);
    private static final Set<String> MODIFIERS = Set.of(PsiModifier.MODIFIERS);

    public static final EnumMap<Transformation.Modifier, String> MODIFIER_TO_STRING = new EnumMap<>(
            Map.of(Transformation.Modifier.PRIVATE, PsiModifier.PRIVATE, Transformation.Modifier.PUBLIC, PsiModifier.PUBLIC, Transformation.Modifier.PROTECTED, PsiModifier.PROTECTED)
    );

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

                apply(pendingATs.remove(new Target.ClassTarget(className)), psiClass, psiClass);

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
                return ((NavigationItem) owner).getName() + " of " + ClassUtil.getJVMClassName(containingClass);
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
        } else if (targetAcc == Transformation.Modifier.DEFAULT || !modifiers.hasModifierProperty(MODIFIER_TO_STRING.get(targetAcc))) {
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
}
