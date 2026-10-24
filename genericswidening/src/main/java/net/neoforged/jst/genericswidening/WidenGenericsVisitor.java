package net.neoforged.jst.genericswidening;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiRecursiveElementVisitor;
import com.intellij.psi.PsiReferenceParameterList;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.util.ClassUtil;
import com.intellij.util.containers.MultiMap;
import net.neoforged.jst.api.PsiHelper;
import net.neoforged.jst.api.Replacements;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

final class WidenGenericsVisitor extends PsiRecursiveElementVisitor {
    private final Replacements replacements;
    private final MultiMap<String, GenericsTarget> methods;

    WidenGenericsVisitor(Replacements replacements, MultiMap<String, GenericsTarget> methods) {
        this.replacements = replacements;
        this.methods = methods;
    }

    @Override
    public void visitElement(@NotNull PsiElement element) {
        if (element instanceof PsiClass psiClass) {
            if (psiClass.getQualifiedName() == null) {
                return;
            }

            String className = ClassUtil.getJVMClassName(psiClass);
            widenGenerics(psiClass, methods.get(className.replace('.', '/')));

            for (PsiClass innerClass : psiClass.getInnerClasses()) {
                visitElement(innerClass);
            }
        }
    }

    @Override
    public void visitFile(@NotNull PsiFile file) {
        file.acceptChildren(this);
    }

    private void widenGenerics(PsiClass psiClass, Collection<GenericsTarget> targets) {
        if (targets.isEmpty()) {
            return;
        }

        for (GenericsTarget target : targets) {
            for (PsiMethod method : psiClass.findMethodsByName(target.methodName(), false)) {
                if (!PsiHelper.getBinaryMethodSignature(method).equals(target.methodDescriptor())) {
                    continue;
                }

                PsiParameterList paramList = method.getParameterList();
                PsiParameter param;
                if (target.genericParameterOrdinal() >= 0) {
                    param = paramList.getParameter(target.genericParameterOrdinal());
                } else {
                    param = findParamByType(paramList, target.genericParameterType());
                }
                if (param == null) {
                    continue;
                }
                widenGeneric(param);
            }
        }
    }

    private static PsiParameter findParamByType(PsiParameterList paramList, String type) {
        for (int i = 0; i < paramList.getParametersCount(); i++) {
            PsiParameter paramCandidate = paramList.getParameter(i);
            if (ClassUtil.getBinaryPresentation(paramCandidate.getType()).equals(type)) {
                return paramCandidate;
            }
        }
        return null;
    }

    private void widenGeneric(PsiParameter param) {
        PsiTypeElement typeElement = findChildOfType(param, PsiTypeElement.class);
        PsiJavaCodeReferenceElement refElement = findChildOfType(typeElement, PsiJavaCodeReferenceElement.class);
        PsiReferenceParameterList refParamList = findChildOfType(refElement, PsiReferenceParameterList.class);
        for (PsiElement child : refParamList.getChildren()) {
            if (child instanceof PsiTypeElement paramTypeElement) {
                replacements.insertBefore(paramTypeElement, "? extends ");
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T findChildOfType(PsiElement element, Class<T> type) {
        for (PsiElement child : element.getChildren()) {
            if (type.isInstance(child)) {
                return (T) child;
            }
        }
        return null;
    }
}
