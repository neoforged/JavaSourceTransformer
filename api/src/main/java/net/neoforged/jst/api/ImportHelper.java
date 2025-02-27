package net.neoforged.jst.api;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiImportStatementBase;
import com.intellij.psi.PsiImportStaticStatement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiPackage;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Helper class used to import classes while processing a source file.
 * @see ImportHelper#get(PsiFile)
 */
public class ImportHelper implements PostProcessReplacer {
    private final PsiJavaFile psiFile;
    private final Map<String, String> importedNames;

    private final Set<String> successfulImports = new HashSet<>();

    public ImportHelper(PsiJavaFile psiFile) {
        this.psiFile = psiFile;

        this.importedNames = new HashMap<>();

        if (psiFile.getPackageStatement() != null) {
            var resolved = psiFile.getPackageStatement().getPackageReference().resolve();
            // We cannot import a class with the name of a class in the package of the file
            if (resolved instanceof PsiPackage pkg) {
                for (PsiClass cls : pkg.getClasses()) {
                    importedNames.put(cls.getName(), cls.getQualifiedName());
                }
            }
        }

        if (psiFile.getImportList() != null) {
            for (PsiImportStatementBase stmt : psiFile.getImportList().getImportStatements()) {
                var res = stmt.resolve();
                if (res instanceof PsiPackage pkg) {
                    // Wildcard package imports will reserve all names of top-level classes in the package
                    for (PsiClass cls : pkg.getClasses()) {
                        importedNames.put(cls.getName(), cls.getQualifiedName());
                    }
                } else if (res instanceof PsiClass cls) {
                    importedNames.put(cls.getName(), cls.getQualifiedName());
                }
            }

            for (PsiImportStaticStatement stmt : psiFile.getImportList().getImportStaticStatements()) {
                var res = stmt.resolve();
                if (res instanceof PsiMethod method) {
                    importedNames.put(method.getName(), method.getName());
                } else if (res instanceof PsiField fld) {
                    importedNames.put(fld.getName(), fld.getName());
                } else if (res instanceof PsiClass cls && stmt.isOnDemand()) {
                    // On-demand imports are static wildcard imports which will reserve the names of
                    // - all static methods available through the imported class
                    for (PsiMethod met : cls.getAllMethods()) {
                        if (met.getModifierList().hasModifierProperty(PsiModifier.STATIC)) {
                            importedNames.put(met.getName(), met.getName());
                        }
                    }

                    // - all fields available through the imported class
                    for (PsiField fld : cls.getAllFields()) {
                        if (fld.getModifierList() != null && fld.getModifierList().hasModifierProperty(PsiModifier.STATIC)) {
                            importedNames.put(fld.getName(), fld.getName());
                        }
                    }

                    // - all inner classes available through the imported class directly
                    for (PsiClass c : cls.getAllInnerClasses()) {
                        importedNames.put(c.getName(), c.getQualifiedName());
                    }

                    // Note: to avoid possible issues, none of the above check for visibility. We prefer to be more conservative to make sure the output sources compile
                }
            }
        }
    }

    @VisibleForTesting
    public boolean canImport(String name) {
        return !importedNames.containsKey(name);
    }

    /**
     * Attempts to import the given fully qualified class name, returning a reference to it which is either
     * its short name (if an import is successful) or the qualified name if not.
     */
    public synchronized String importClass(String cls) {
        var clsByDot = cls.split("\\.");
        // We do not try to import classes in the default package or classes already imported
        if (clsByDot.length == 1 || successfulImports.contains(cls)) return clsByDot[clsByDot.length - 1];
        var name = clsByDot[clsByDot.length - 1];

        if (Objects.equals(importedNames.get(name), cls)) {
            return name;
        }

        if (canImport(name)) {
            successfulImports.add(cls);
            return name;
        }

        return cls;
    }

    @Override
    public void process(Replacements replacements) {
        if (successfulImports.isEmpty()) return;

        var insertion = successfulImports.stream()
                .sorted()
                .map(s -> "import " + s + ";")
                .collect(Collectors.joining("\n"));

        if (psiFile.getImportList() != null && psiFile.getImportList().getLastChild() != null) {
            var lastImport = psiFile.getImportList().getLastChild();
            replacements.insertAfter(lastImport, "\n\n" + insertion);
        } else {
            replacements.insertBefore(psiFile.getClasses()[0], insertion + "\n\n");
        }
    }

    @Nullable
    public static ImportHelper get(PsiFile file) {
        return file instanceof PsiJavaFile j ? get(j) : null;
    }

    public static ImportHelper get(PsiJavaFile file) {
        return PostProcessReplacer.getOrCreateReplacer(file, ImportHelper.class, k -> new ImportHelper(file));
    }
}
