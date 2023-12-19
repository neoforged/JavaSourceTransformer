import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.intellij.core.CoreApplicationEnvironment;
import com.intellij.core.CoreJavaFileManager;
import com.intellij.core.JavaCoreApplicationEnvironment;
import com.intellij.core.JavaCoreProjectEnvironment;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.lang.jvm.facade.JvmElementProvider;
import com.intellij.mock.MockProject;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.application.TransactionGuardImpl;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.util.TextRange;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaModuleSystem;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFinder;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiJavaDocumentedElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiNameHelper;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiRecursiveElementVisitor;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.augment.PsiAugmentProvider;
import com.intellij.psi.impl.PsiElementFinderImpl;
import com.intellij.psi.impl.PsiNameHelperImpl;
import com.intellij.psi.impl.PsiTreeChangePreprocessor;
import com.intellij.psi.impl.file.impl.JavaFileManager;
import com.intellij.psi.impl.source.tree.JavaTreeGenerator;
import com.intellij.psi.impl.source.tree.TreeGenerator;
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.parchmentmc.feather.io.gson.MDCGsonAdapterFactory;
import org.parchmentmc.feather.io.gson.SimpleVersionAdapter;
import org.parchmentmc.feather.mapping.MappingDataContainer;
import org.parchmentmc.feather.mapping.VersionedMappingDataContainer;
import org.parchmentmc.feather.util.SimpleVersion;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Reference for out-of-IDE usage of the IntelliJ Java parser is from the Kotlin compiler
 * https://github.com/JetBrains/kotlin/blob/22aa9ee65f759ad21aeaeb8ad9ac0b123b2c32fe/compiler/cli/cli-base/src/org/jetbrains/kotlin/cli/jvm/compiler/KotlinCoreEnvironment.kt#L108
 */
public class ApplyParchmentToSourceJar implements AutoCloseable {

    private final VersionedMappingDataContainer parchmentDatabase;

    private final Path tempDir;
    private final MockProject project;
    private final JavaCoreProjectEnvironment javaEnv;
    private final PsiFileFactory psiFileFactory;
    private int maxQueueDepth = 0;

    public ApplyParchmentToSourceJar(Path parchmentZipPath) throws IOException {
        parchmentDatabase = openParchmentFile(parchmentZipPath);
        tempDir = Files.createTempDirectory("applyparchment");

        System.setProperty("idea.config.path", tempDir.toAbsolutePath().toString());

        var appEnv = new JavaCoreApplicationEnvironment(() -> {
        }) {
        };

        // When any service or extension point is missing, check JavaPsiPlugin.xml in classpath and grab the definition
        appEnv.registerApplicationService(com.intellij.psi.util.JavaClassSupers.class, new com.intellij.psi.impl.JavaClassSupersImpl());
        appEnv.registerApplicationService(com.intellij.pom.java.InternalPersistentJavaLanguageLevelReaderService.class, new com.intellij.pom.java.InternalPersistentJavaLanguageLevelReaderService.DefaultImpl());
        appEnv.registerApplicationService(TransactionGuard.class, new TransactionGuardImpl());

        CoreApplicationEnvironment.registerExtensionPoint(
                appEnv.getApplication().getExtensionArea(),
                PsiAugmentProvider.EP_NAME,
                PsiAugmentProvider.class
        );
        CoreApplicationEnvironment.registerExtensionPoint(
                appEnv.getApplication().getExtensionArea(),
                JavaModuleSystem.EP_NAME,
                JavaModuleSystem.class
        );
        CoreApplicationEnvironment.registerExtensionPoint(
                appEnv.getApplication().getExtensionArea(),
                TreeGenerator.EP_NAME,
                TreeGenerator.class
        );
        appEnv.getApplication().getExtensionArea().getExtensionPoint(TreeGenerator.EP_NAME).registerExtension(new JavaTreeGenerator());

        javaEnv = new JavaCoreProjectEnvironment(
                () -> {
                },
                appEnv
        );

        project = javaEnv.getProject();
        project.registerService(PsiNameHelper.class, PsiNameHelperImpl.class);

        CoreApplicationEnvironment.registerExtensionPoint(
                project.getExtensionArea(),
                PsiTreeChangePreprocessor.EP.getName(),
                PsiTreeChangePreprocessor.class
        );
        CoreApplicationEnvironment.registerExtensionPoint(
                project.getExtensionArea(),
                PsiElementFinder.EP.getName(),
                PsiElementFinder.class
        );
        CoreApplicationEnvironment.registerExtensionPoint(
                project.getExtensionArea(),
                JvmElementProvider.EP_NAME,
                JvmElementProvider.class
        );
        PsiElementFinder.EP.getPoint(project).registerExtension(new PsiElementFinderImpl(project), () -> {});

        LanguageLevelProjectExtension.getInstance(project).setLanguageLevel(LanguageLevel.JDK_17);

        psiFileFactory = PsiFileFactory.getInstance(project);
    }

    private static VersionedMappingDataContainer openParchmentFile(Path parchmentFile) throws IOException {
        try (var zf = new ZipFile(parchmentFile.toFile())) {
            var parchmentJsonEntry = zf.getEntry("parchment.json");
            if (parchmentJsonEntry == null || parchmentJsonEntry.isDirectory()) {
                throw new FileNotFoundException("Could not locate parchment.json at the root of ZIP-File " + parchmentFile);
            }

            Gson gson = new GsonBuilder()
                    .registerTypeAdapterFactory(new MDCGsonAdapterFactory())
                    .registerTypeAdapter(SimpleVersion.class, new SimpleVersionAdapter())
                    .create();
            try (var inputStream = zf.getInputStream(parchmentJsonEntry)) {
                String jsonString = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                return gson.fromJson(jsonString, VersionedMappingDataContainer.class);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");

        var inputPath = Paths.get(args[0]);
        var output = Paths.get(args[2]);
        var parchmentZipPath = Paths.get(args[3]);

        try (var applyParchment = new ApplyParchmentToSourceJar(parchmentZipPath)) {
            applyParchment.apply(inputPath, output);
        }

    }

    private void apply(Path inputPath, Path outputPath) throws IOException, InterruptedException {

        var sourceJarRoot = javaEnv.getEnvironment().getJarFileSystem().findFileByPath(inputPath + "!/");
        if (sourceJarRoot == null) {
            throw new FileNotFoundException("Cannot find JAR-File " + inputPath);
        }

        javaEnv.addSourcesToClasspath(sourceJarRoot);

        var javaFileManager = (CoreJavaFileManager) JavaFileManager.getInstance(project);
        javaFileManager.addToClasspath(sourceJarRoot);

//        Files.readAllLines(Paths.get(librariesPath))
//                .stream()
//                .filter(l -> l.startsWith("-e="))
//                .map(l -> l.substring(3))
//                .map(File::new)
//                .forEach(file -> {
//                    if (!file.exists()) {
//                        throw new UncheckedIOException(new FileNotFoundException(file.getAbsolutePath()));
//                    }
//                    javaEnv.addJarToClassPath(file);
//                });

        try (var zin = new ZipInputStream(Files.newInputStream(inputPath));
             var fout = Files.newOutputStream(outputPath);
             var asyncZout = new OrderedWorkQueue(new ZipOutputStream(fout), maxQueueDepth)) {

            for (var entry = zin.getNextEntry(); entry != null; entry = zin.getNextEntry()) {
                var originalContentBytes = zin.readAllBytes();

                var entryPath = entry.getName();
                if (entryPath.endsWith(".java")) {
                    asyncZout.submitAsync(entry, () -> {
                        return transformSource(entryPath, originalContentBytes);
                    });
                } else {
                    asyncZout.submit(entry, originalContentBytes);
                }
            }
        }
    }

    private byte[] transformSource(String path, byte[] originalContentBytes) {
        String originalContent = new String(originalContentBytes, StandardCharsets.UTF_8);
        var psiFile = psiFileFactory.createFileFromText(path, JavaLanguage.INSTANCE, originalContent);

        List<Replacement> replacements = new ArrayList<>();

        var v = new PsiRecursiveElementVisitor() {
            PsiClass currentPsiClass;
            MappingDataContainer.ClassData currentClass;

            @Override
            public void visitElement(@NotNull PsiElement element) {
                if (element instanceof PsiClass psiClass) {
                    currentPsiClass = psiClass;
                    var jvmClassName = ClassUtil.getJVMClassName(psiClass);
                    if (jvmClassName == null) {
                        // Anonymous class?
                        currentClass = null;
                    } else {
                        var className = jvmClassName.replace('.', '/');
                        currentClass = parchmentDatabase.getClass(className);
                    }

                    if (currentClass == null) {
                        return; // Skip classes without mapping data
                    }

                    // Add javadoc if available
                    applyJavadoc(psiClass, currentClass.getJavadoc(), replacements);
                } else if (element instanceof PsiField psiField) {
                    // sanity check
                    if (psiField.getContainingClass() != currentPsiClass) {
                        return;
                    }

                    var fieldData = currentClass.getField(psiField.getName());
                    if (fieldData != null) {
                        // Add javadoc if available
                        applyJavadoc(psiField, fieldData.getJavadoc(), replacements);
                    }
                } else if (element instanceof PsiMethod method) {
                    // sanity check
                    if (method.getContainingClass() != currentPsiClass) {
                        return;
                    }

                    var methodSignature = ClassUtil.getAsmMethodSignature(method);

                    var methodData = currentClass.getMethod(method.getName(), methodSignature);
                    if (methodData != null) {
                        // Add javadoc if available
                        applyJavadoc(method, methodData.getJavadoc(), replacements);

                        PsiParameter[] parameters = method.getParameterList().getParameters();
                        for (int i = 0; i < parameters.length; i++) {
                            var psiParameter = parameters[i];
                            // We cannot replace parameters with no name, sadly
                            if (psiParameter.getNameIdentifier() == null) {
                                continue;
                            }
                            var paramData = methodData.getParameter((byte) i);
                            if (paramData != null) {
                                // Find and replace the parameter identifier
                                replacements.add(Replacement.create(
                                        psiParameter.getNameIdentifier(), paramData.getName()
                                ));
                                // Find usages of the parameter in the method body and replace those as well
                                PsiTreeUtil.processElements(method.getBody(), (e) -> {
                                    if (e instanceof PsiReferenceExpression refExpr && refExpr.isReferenceTo(psiParameter)) {
                                        replacements.add(Replacement.create(
                                                refExpr.getReferenceNameElement(), paramData.getName()
                                        ));
                                    }
                                    return true;
                                });
                            }
                        }
                    }

                }

                element.acceptChildren(this);
            }
        };
        v.visitElement(psiFile);

        // If no replacements were made, just stream the original content into the destination file
        if (replacements.isEmpty()) {
            return originalContentBytes;
        }

        replacements.sort(Replacement.COMPARATOR);

        var writer = new StringBuilder();
        // Copy up until the first replacement

        writer.append(originalContent, 0, replacements.get(0).range().getStartOffset());
        for (int i = 0; i < replacements.size(); i++) {
            var replacement = replacements.get(i);
            var range = replacement.range();
            if (i > 0) {
                // Copy between previous and current replacement verbatim
                var previousReplacement = replacements.get(i - 1);
                // validate that replacement ranges are non-overlapping
                if (previousReplacement.range().getEndOffset() > range.getStartOffset()) {
                    throw new IllegalStateException("Trying to replace overlapping ranges: "
                            + replacement + " and " + previousReplacement);
                }

                writer.append(
                        originalContent,
                        previousReplacement.range().getEndOffset(),
                        range.getStartOffset()
                );
            }
            writer.append(replacement.newText());
        }
        writer.append(originalContent, replacements.get(replacements.size() - 1).range().getEndOffset(), originalContent.length());
        return writer.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void applyJavadoc(PsiJavaDocumentedElement method, List<String> javadoc, List<Replacement> replacements) {
        if (!javadoc.isEmpty()) {
            var existingDocComment = method.getDocComment();
            if (existingDocComment != null) {
                // replace right after
                var textRange = existingDocComment.getTextRange();
                replacements.add(
                        new Replacement(
                                new TextRange(textRange.getEndOffset(), textRange.getEndOffset()),
                                "/**\n"
                                        + javadoc.stream().map(line -> " * " + line + "\n")
                                        .collect(Collectors.joining())
                                        + " */\n"
                        )
                );
            } else {
                // Insert right before the method
                int startOffset;
                String indentText;
                if (method.getPrevSibling() != null && method.getPrevSibling() instanceof PsiWhiteSpace psiWhiteSpace) {
                    var lastNewline = psiWhiteSpace.getText().lastIndexOf('\n');
                    var wsRange = psiWhiteSpace.getTextRange();
                    // No newline, just take the entire whitespace as indent, and insert before
                    if (lastNewline == -1) {
                        indentText = " ".repeat(psiWhiteSpace.getTextLength());
                        startOffset = wsRange.getEndOffset();
                    } else {
                        // Otherwise we inherit the whitespace as our own indent
                        indentText = " ".repeat(psiWhiteSpace.getTextLength() - lastNewline - 1);
                        startOffset = wsRange.getEndOffset();
                    }
                } else {
                    indentText = "";
                    startOffset = method.getTextRange().getStartOffset();
                }
                replacements.add(
                        new Replacement(
                                new TextRange(startOffset, startOffset),
                                "/**\n"
                                        + javadoc.stream().map(line -> indentText + " * " + line + "\n")
                                        .collect(Collectors.joining())
                                        + indentText + " */\n" + indentText
                        )
                );
            }
        }
    }

    @Override
    public void close() throws IOException {
        Files.deleteIfExists(tempDir);
    }

}
