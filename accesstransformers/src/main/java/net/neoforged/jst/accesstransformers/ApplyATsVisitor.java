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
import net.neoforged.accesstransformer.parser.AccessTransformerFiles;
import net.neoforged.accesstransformer.parser.Target;
import net.neoforged.accesstransformer.parser.Transformation;
import net.neoforged.jst.api.Replacements;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

class ApplyATsVisitor extends PsiRecursiveElementVisitor {
    private static final List<String> ACCESS_MODIFIERS = List.of(PsiModifier.PUBLIC, PsiModifier.PRIVATE, PsiModifier.PROTECTED);
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
                if (!ats.containsClassTarget(className)) return; // Skip this class and all its children

                apply(ats.getAccessTransformers().get(new Target.ClassTarget(className)), psiClass.getModifierList());
                var fieldWildcard = ats.getAccessTransformers().get(new Target.WildcardFieldTarget(className));
                if (fieldWildcard != null) {
                    for (PsiField field : psiClass.getFields()) {
                        apply(fieldWildcard, field.getModifierList());
                    }
                }

                var methodWildcard = ats.getAccessTransformers().get(new Target.WildcardMethodTarget(className));
                if (methodWildcard != null) {
                    for (PsiMethod method : psiClass.getMethods()) {
                        apply(methodWildcard, method.getModifierList());
                    }
                }
            }
        } else if (element instanceof PsiField field) {
            final var cls = field.getContainingClass();
            if (cls != null && cls.getQualifiedName() != null) {
                String className = ClassUtil.getJVMClassName(cls);
                apply(ats.getAccessTransformers().get(new Target.FieldTarget(className, field.getName())), field.getModifierList());
            }
        } else if (element instanceof PsiMethod method) {
            final var cls = method.getContainingClass();
            if (cls != null && cls.getQualifiedName() != null) {
                String className = ClassUtil.getJVMClassName(cls);
                apply(ats.getAccessTransformers().get(new Target.MethodTarget(className, method.getName(), ClassUtil.getAsmMethodSignature(method))), method.getModifierList());
            }
        }

        element.acceptChildren(this);
    }

    private void apply(@Nullable Transformation at, PsiModifierList modifiers) {
        if (at == null || !at.isValid()) return;

        var targetAcc = at.modifier();
        if (targetAcc == Transformation.Modifier.DEFAULT || !modifiers.hasModifierProperty(MODIFIER_TO_STRING.get(targetAcc))) {
            final var existingModifier = Arrays.stream(modifiers.getChildren())
                    .filter(el -> el instanceof PsiKeyword)
                    .map(el -> (PsiKeyword) el)
                    .filter(kw -> ACCESS_MODIFIERS.contains(kw.getText()))
                    .findFirst();

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
                        replacements.insertBefore(modifiers, MODIFIER_TO_STRING.get(targetAcc) + " ");
                    }
                }
            }
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
}
