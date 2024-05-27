package net.neoforged.jst.accesstransformers;

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

    public ApplyATsVisitor(AccessTransformerFiles ats, Replacements replacements) {
        this.ats = ats;
        this.replacements = replacements;
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

                apply(ats.getAccessTransformers().get(new Target.ClassTarget(className)), psiClass, psiClass);
                var fieldWildcard = ats.getAccessTransformers().get(new Target.WildcardFieldTarget(className));
                if (fieldWildcard != null) {
                    for (PsiField field : psiClass.getFields()) {
                        apply(fieldWildcard, field, psiClass);
                    }
                }

                var methodWildcard = ats.getAccessTransformers().get(new Target.WildcardMethodTarget(className));
                if (methodWildcard != null) {
                    for (PsiMethod method : psiClass.getMethods()) {
                        apply(methodWildcard, method, psiClass);
                    }
                }
            }
        } else if (element instanceof PsiField field) {
            final var cls = field.getContainingClass();
            if (cls != null && cls.getQualifiedName() != null) {
                String className = ClassUtil.getJVMClassName(cls);
                apply(ats.getAccessTransformers().get(new Target.FieldTarget(className, field.getName())), field, cls);
            }
        } else if (element instanceof PsiMethod method) {
            final var cls = method.getContainingClass();
            if (cls != null && cls.getQualifiedName() != null) {
                String className = ClassUtil.getJVMClassName(cls);
                apply(ats.getAccessTransformers().get(new Target.MethodTarget(className, PsiHelper.getBinaryMethodName(method), PsiHelper.getBinaryMethodSignature(method))), method, cls);
            }
        }

        element.acceptChildren(this);
    }

    // TODO - proper logging when an AT can't be applied
    private void apply(@Nullable Transformation at, PsiModifierListOwner owner, PsiClass containingClass) {
        if (at == null || !at.isValid()) return;
        var modifiers = owner.getModifierList();

        var targetAcc = at.modifier();

        // If we're modifying a non-static interface method we can only make it public, meaning it must be defined as default
        if (containingClass.isInterface() && owner instanceof PsiMethod && !modifiers.hasModifierProperty(PsiModifier.STATIC)) {
            if (targetAcc != Transformation.Modifier.PUBLIC) {
                System.err.println("Non-static interface methods can only be made public");
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
}
