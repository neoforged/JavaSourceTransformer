package net.neoforged.jst.parchment;

import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import net.neoforged.jst.api.PsiHelper;
import net.neoforged.jst.parchment.namesanddocs.NamesAndDocsDatabase;
import net.neoforged.jst.parchment.namesanddocs.NamesAndDocsForClass;
import net.neoforged.jst.parchment.namesanddocs.NamesAndDocsForMethod;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public class PsiParchmentHelper {
    // Keys for attaching mapping data to a PsiClass. Used to prevent multiple lookups for the same class/method.
    private static final Key<Optional<NamesAndDocsForClass>> CLASS_DATA_KEY = Key.create("names_and_docs_for_class");
    private static final Key<Optional<NamesAndDocsForMethod>> METHOD_DATA_KEY = Key.create("names_and_docs_for_method");
    private static final Key<Set<String>> ALL_FIELD_NAMES = Key.create("all_field_names");

    @SuppressWarnings("OptionalAssignedToNull")
    @Nullable
    public static NamesAndDocsForClass getClassData(NamesAndDocsDatabase namesAndDocs, @Nullable PsiClass psiClass) {
        if (psiClass == null) {
            return null;
        }
        var classData = psiClass.getUserData(CLASS_DATA_KEY);
        if (classData != null) {
            return classData.orElse(null);
        } else {
            var sb = new StringBuilder();
            PsiHelper.getBinaryClassName(psiClass, sb);
            if (sb.isEmpty()) {
                classData = Optional.empty();
            } else {
                classData = Optional.ofNullable(namesAndDocs.getClass(sb.toString()));
            }
            psiClass.putUserData(CLASS_DATA_KEY, classData);
            return classData.orElse(null);
        }
    }

    @SuppressWarnings("OptionalAssignedToNull")
    @Nullable
    public static NamesAndDocsForMethod getMethodData(NamesAndDocsDatabase namesAndDocs, @Nullable PsiMethod psiMethod) {
        if (psiMethod == null) {
            return null;
        }
        var methodData = psiMethod.getUserData(METHOD_DATA_KEY);
        if (methodData != null) {
            return methodData.orElse(null);
        } else {
            methodData = Optional.empty();
            var classData = getClassData(namesAndDocs, psiMethod.getContainingClass());
            if (classData != null) {
                var methodName = PsiHelper.getBinaryMethodName(psiMethod);
                var signatures = PsiHelper.getOverloadedSignatures(psiMethod);
                while (signatures.hasNext() && methodData.isEmpty()) {
                    methodData = Optional.ofNullable(classData.getMethod(methodName, signatures.next()));
                }
            }

            psiMethod.putUserData(METHOD_DATA_KEY, methodData);
            return methodData.orElse(null);
        }
    }

    public static Set<String> getAllFieldNames(PsiClass clazz) {
        var existing = clazz.getUserData(ALL_FIELD_NAMES);
        if (existing != null) {
            return existing;
        }

        existing = new HashSet<>();
        for (PsiField field : clazz.getAllFields()) {
            existing.add(field.getName());
        }
        clazz.putUserData(ALL_FIELD_NAMES, existing);
        return existing;
    }
}
