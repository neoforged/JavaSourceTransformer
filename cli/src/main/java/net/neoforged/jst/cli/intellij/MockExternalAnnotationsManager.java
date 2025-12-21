package net.neoforged.jst.cli.intellij;

import com.intellij.codeInsight.ExternalAnnotationsManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiNameValuePair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class MockExternalAnnotationsManager extends ExternalAnnotationsManager {
    @Override
    public boolean hasAnnotationRootsForFile(@NotNull VirtualFile file) {
        return false;
    }

    @Override
    public boolean isExternalAnnotation(@NotNull PsiAnnotation annotation) {
        return false;
    }

    @Override
    public @Nullable PsiAnnotation findExternalAnnotation(@NotNull PsiModifierListOwner listOwner, @NotNull String annotationFQN) {
        return null;
    }

    @Override
    public @NotNull List<PsiAnnotation> findExternalAnnotations(@NotNull PsiModifierListOwner listOwner, @NotNull String annotationFQN) {
        return List.of();
    }

    @Override
    public boolean isExternalAnnotationWritable(@NotNull PsiModifierListOwner listOwner, @NotNull String annotationFQN) {
        return false;
    }

    @Override
    public @NotNull PsiAnnotation @NotNull [] findExternalAnnotations(@NotNull PsiModifierListOwner listOwner) {
        return new PsiAnnotation[0];
    }

    @Override
    public @Nullable List<PsiAnnotation> findDefaultConstructorExternalAnnotations(@NotNull PsiClass aClass) {
        return List.of();
    }

    @Override
    public @Nullable List<PsiAnnotation> findDefaultConstructorExternalAnnotations(@NotNull PsiClass aClass, @NotNull String annotationFQN) {
        return List.of();
    }

    @Override
    public void annotateExternally(@NotNull PsiModifierListOwner listOwner, @NotNull String annotationFQName, @NotNull PsiFile fromFile, PsiNameValuePair @Nullable [] value) throws CanceledConfigurationException {

    }

    @Override
    public boolean deannotate(@NotNull PsiModifierListOwner listOwner, @NotNull String annotationFQN) {
        return false;
    }

    @Override
    public boolean editExternalAnnotation(@NotNull PsiModifierListOwner listOwner, @NotNull String annotationFQN, PsiNameValuePair @Nullable [] value) {
        return false;
    }

    @Override
    public @NotNull AnnotationPlace chooseAnnotationsPlaceNoUi(@NotNull PsiElement element) {
        return AnnotationPlace.NOWHERE;
    }

    @Override
    public @NotNull AnnotationPlace chooseAnnotationsPlace(@NotNull PsiElement element) {
        return AnnotationPlace.NOWHERE;
    }

    @Override
    public @Nullable List<PsiFile> findExternalAnnotationsFiles(@NotNull PsiModifierListOwner listOwner) {
        return List.of();
    }

    @Override
    public boolean hasConfiguredAnnotationRoot(@NotNull PsiModifierListOwner owner) {
        return false;
    }
}
