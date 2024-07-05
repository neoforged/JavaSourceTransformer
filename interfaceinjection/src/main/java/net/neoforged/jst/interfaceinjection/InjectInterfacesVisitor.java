package net.neoforged.jst.interfaceinjection;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiRecursiveElementVisitor;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.ClassUtil;
import com.intellij.util.containers.MultiMap;
import net.neoforged.jst.api.Replacements;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.stream.Collectors;

class InjectInterfacesVisitor extends PsiRecursiveElementVisitor {
    private final Replacements replacements;
    private final MultiMap<String, String> interfaces;
    private final StubStore stubs;

    @Nullable
    private final String marker;

    InjectInterfacesVisitor(Replacements replacements, MultiMap<String, String> interfaces, StubStore stubs, String marker) {
        this.replacements = replacements;
        this.interfaces = interfaces;
        this.stubs = stubs;
        this.marker = marker;
    }

    @Override
    public void visitElement(@NotNull PsiElement element) {
        if (element instanceof PsiClass psiClass) {
            if (psiClass.getQualifiedName() == null) return;
            String className = ClassUtil.getJVMClassName(psiClass);
            inject(psiClass, interfaces.get(className.replace('.', '/')));

            for (PsiClass innerClass : psiClass.getInnerClasses()) {
                visitElement(innerClass);
            }
        }
    }

    @Override
    public void visitFile(@NotNull PsiFile file) {
        file.acceptChildren(this);
    }

    private void inject(PsiClass psiClass, Collection<String> targets) {
        // We cannot add implements clauses to anonymous or unnamed classes
        if (targets.isEmpty() || psiClass.getImplementsList() == null) return;

        var interfaceImplementation = targets.stream()
                .distinct().map(stubs::createStub)
                .map(this::decorate)
                .collect(Collectors.joining(", "));

        var implementsList = psiClass.isInterface() ? psiClass.getExtendsList() : psiClass.getImplementsList();
        if (implementsList.getChildren().length == 0) {
            StringBuilder text = new StringBuilder();

            // `public class Cls{}` is valid, but we cannot inject the implements exactly next to the class name, so we need
            // to make sure that we have spacing
            if (!(psiClass.getLBrace().getPrevSibling() instanceof PsiWhiteSpace)) {
                text.append(' ');
            }
            text.append(psiClass.isInterface() ? "extends" : "implements").append(' ');
            text.append(interfaceImplementation);
            text.append(' ');

            replacements.insertBefore(psiClass.getLBrace(), text.toString());
        } else {
            replacements.insertAfter(implementsList.getLastChild(), ", " + interfaceImplementation);
        }
    }

    private String decorate(String iface) {
        if (marker == null) {
            return iface;
        }
        return "@" + marker + " " + iface;
    }
}
