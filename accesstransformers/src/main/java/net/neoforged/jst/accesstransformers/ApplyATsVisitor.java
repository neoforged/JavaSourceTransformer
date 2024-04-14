package net.neoforged.jst.accesstransformers;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiKeyword;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiRecursiveElementVisitor;
import com.intellij.psi.util.ClassUtil;
import net.neoforged.accesstransformer.api.AccessTransformer;
import net.neoforged.accesstransformer.api.TargetType;
import net.neoforged.jst.api.Replacements;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

class ApplyATsVisitor extends PsiRecursiveElementVisitor {
    private static final List<String> ACCESS_MODIFIERS = List.of(PsiModifier.PUBLIC, PsiModifier.PRIVATE, PsiModifier.PROTECTED);
    public static final EnumMap<AccessTransformer.Modifier, String> MODIFIER_TO_STRING = new EnumMap<>(
            Map.of(AccessTransformer.Modifier.PRIVATE, PsiModifier.PRIVATE, AccessTransformer.Modifier.PUBLIC, PsiModifier.PUBLIC, AccessTransformer.Modifier.PROTECTED, PsiModifier.PROTECTED)
    );

    private final Map<String, List<AccessTransformer>> ats;
    private final Replacements replacements;

    public ApplyATsVisitor(Map<String, List<AccessTransformer>> ats, Replacements replacements) {
        this.ats = ats;
        this.replacements = replacements;
    }

    @Override
    public void visitElement(@NotNull PsiElement element) {
        if (element instanceof PsiClass psiClass) {
            if (psiClass.getQualifiedName() != null) {
                String className = ClassUtil.getJVMClassName(psiClass);
                ats.getOrDefault(className, List.of())
                        .forEach(accessTransformer -> {
                            if (accessTransformer.getTarget().getType() == TargetType.CLASS && accessTransformer.getTarget().targetName().equals(className)) {
                                apply(accessTransformer, psiClass.getModifierList());
                            }
                        });
            }
        } else if (element instanceof PsiField field) {
            final var cls = field.getContainingClass();
            if (cls != null && cls.getQualifiedName() != null) {
                String className = ClassUtil.getJVMClassName(cls);
                ats.getOrDefault(className, List.of())
                        .forEach(accessTransformer -> {
                            if ((accessTransformer.getTarget().getType() == TargetType.FIELD && accessTransformer.getTarget().targetName().equals(field.getName())) || accessTransformer.getTarget().getWildcardTarget() == TargetType.FIELD) {
                                apply(accessTransformer, field.getModifierList());
                            }
                        });
            }
        } else if (element instanceof PsiMethod method) {
            final var cls = method.getContainingClass();
            if (cls != null && cls.getQualifiedName() != null) {
                String className = ClassUtil.getJVMClassName(cls);
                ats.getOrDefault(className, List.of())
                        .forEach(accessTransformer -> {
                            if ((accessTransformer.getTarget().getType() == TargetType.METHOD && accessTransformer.getTarget().targetName().equals(
                                    method.getName() + ClassUtil.getAsmMethodSignature(method)
                            )) || accessTransformer.getTarget().getWildcardTarget() == TargetType.METHOD) {
                                apply(accessTransformer, method.getModifierList());
                            }
                        });
            }
        }

        element.acceptChildren(this);
    }

    private void apply(AccessTransformer at, PsiModifierList modifiers) {
        if (!at.isValid()) return;

        var targetAcc = at.getTargetAccess();
        if (targetAcc == AccessTransformer.Modifier.DEFAULT || !modifiers.hasModifierProperty(MODIFIER_TO_STRING.get(targetAcc))) {
            final var existingModifier = Arrays.stream(modifiers.getChildren())
                    .filter(el -> el instanceof PsiKeyword)
                    .map(el -> (PsiKeyword) el)
                    .filter(kw -> ACCESS_MODIFIERS.contains(kw.getText()))
                    .findFirst();

            if (targetAcc == AccessTransformer.Modifier.DEFAULT) {
                existingModifier.ifPresent(replacements::remove);
            } else {
                if (existingModifier.isPresent()) {
                    replacements.replace(existingModifier.get(), MODIFIER_TO_STRING.get(targetAcc));
                } else {
                    if (modifiers.getChildren().length == 0) {
                        // Empty modifiers are blank so we basically replace them
                        replacements.insertAfter(modifiers, MODIFIER_TO_STRING.get(targetAcc) + " ");
                    } else {
                        replacements.insertBefore(modifiers, MODIFIER_TO_STRING.get(targetAcc) + " ");
                    }
                }
            }
        }

        var finalState = at.getTargetFinalState();
        if (finalState == AccessTransformer.FinalState.REMOVEFINAL && modifiers.hasModifierProperty(PsiModifier.FINAL)) {
            Arrays.stream(modifiers.getChildren())
                    .filter(el -> el instanceof PsiKeyword)
                    .map(el -> (PsiKeyword) el)
                    .filter(kw -> kw.getText().equals(PsiModifier.FINAL))
                    .findFirst()
                    .ifPresent(replacements::remove);
        }
    }
}
