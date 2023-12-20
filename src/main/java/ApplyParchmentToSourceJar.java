import com.intellij.core.CoreApplicationEnvironment;
import com.intellij.core.JavaCoreApplicationEnvironment;
import com.intellij.core.JavaCoreProjectEnvironment;
import com.intellij.lang.jvm.facade.JvmElementProvider;
import com.intellij.mock.MockProject;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.application.TransactionGuardImpl;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.openapi.vfs.impl.ZipHandler;
import com.intellij.pom.java.InternalPersistentJavaLanguageLevelReaderService;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaModuleSystem;
import com.intellij.psi.PsiElementFinder;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiNameHelper;
import com.intellij.psi.augment.PsiAugmentProvider;
import com.intellij.psi.impl.PsiElementFinderImpl;
import com.intellij.psi.impl.PsiNameHelperImpl;
import com.intellij.psi.impl.PsiTreeChangePreprocessor;
import com.intellij.psi.impl.source.tree.JavaTreeGenerator;
import com.intellij.psi.impl.source.tree.TreeGenerator;
import com.intellij.psi.util.JavaClassSupers;
import modules.CoreJrtFileSystem;
import namesanddocs.NameAndDocSourceLoader;
import namesanddocs.NamesAndDocsDatabase;
import org.jetbrains.annotations.NotNull;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Reference for out-of-IDE usage of the IntelliJ Java parser is from the Kotlin compiler
 * https://github.com/JetBrains/kotlin/blob/22aa9ee65f759ad21aeaeb8ad9ac0b123b2c32fe/compiler/cli/cli-base/src/org/jetbrains/kotlin/cli/jvm/compiler/KotlinCoreEnvironment.kt#L108
 */
public class ApplyParchmentToSourceJar implements AutoCloseable {
    private final NamesAndDocsDatabase namesAndDocs;

    private final Path tempDir;
    private final MockProject project;
    private final JavaCoreProjectEnvironment javaEnv;
    private final PsiManager psiManager;
    private int maxQueueDepth = 50;
    private boolean enableJavadoc = true;
    private final Disposable rootDisposable;

    public ApplyParchmentToSourceJar(Path javaHome, NamesAndDocsDatabase namesAndDocs) throws IOException {
        this.namesAndDocs = namesAndDocs;
        tempDir = Files.createTempDirectory("applyparchment");
        this.rootDisposable = Disposer.newDisposable();
        System.setProperty("idea.home.path", tempDir.toAbsolutePath().toString());

        // IDEA requires a config directory, even if it's empty
        PathManager.setExplicitConfigPath(tempDir.toAbsolutePath().toString());
        Registry.markAsLoaded(); // Avoids warnings about config not being loaded

        var appEnv = new JavaCoreApplicationEnvironment(rootDisposable) {
            @Override
            protected VirtualFileSystem createJrtFileSystem() {
                return new CoreJrtFileSystem();
            }
        };
        initAppExtensionsAndServices(appEnv);

        javaEnv = new JavaCoreProjectEnvironment(rootDisposable, appEnv);

        ClasspathSetup.addJdkModules(javaHome, javaEnv);

        project = javaEnv.getProject();

        initProjectExtensionsAndServices(project);

        LanguageLevelProjectExtension.getInstance(project).setLanguageLevel(LanguageLevel.JDK_17);

        psiManager = PsiManager.getInstance(project);
    }


    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");

        Path inputPath = null, outputPath = null, namesAndDocsPath = null, librariesPath = null;
        boolean enableJavadoc = true;
        int queueDepth = 50;

        for (int i = 0; i < args.length; i++) {
            var arg = args[i];
            switch (arg) {
                case "--in":
                    if (i + 1 >= args.length) {
                        System.err.println("Missing argument for --in");
                        System.exit(1);
                    }
                    inputPath = Paths.get(args[++i]);
                    break;
                case "--out":
                    if (i + 1 >= args.length) {
                        System.err.println("Missing argument for --out");
                        System.exit(1);
                    }
                    outputPath = Paths.get(args[++i]);
                    break;
                case "--libraries":
                    if (i + 1 >= args.length) {
                        System.err.println("Missing argument for --libraries");
                        System.exit(1);
                    }
                    librariesPath = Paths.get(args[++i]);
                    break;
                case "--names":
                    if (i + 1 >= args.length) {
                        System.err.println("Missing argument for --names");
                        System.exit(1);
                    }
                    namesAndDocsPath = Paths.get(args[++i]);
                    break;
                case "--skip-javadoc":
                    enableJavadoc = false;
                    break;
                case "--queue-depth":
                    if (i + 1 >= args.length) {
                        System.err.println("Missing argument for --queue-depth");
                        System.exit(1);
                    }
                    queueDepth = Integer.parseUnsignedInt(args[++i]);
                    break;
                case "--help":
                    printUsage(System.out);
                    System.exit(0);
                    break;
                default:
                    System.err.println("Unknown argument: " + arg);
                    printUsage(System.err);
                    System.exit(1);
                    break;
            }
        }

        if (inputPath == null || outputPath == null || namesAndDocsPath == null) {
            System.err.println("Missing arguments");
            printUsage(System.err);
            System.exit(1);
        }

        var namesAndDocs = NameAndDocSourceLoader.load(namesAndDocsPath);

        // Add the Java Runtime we are currently running in
        var javaHome = Paths.get(System.getProperty("java.home"));

        try (var applyParchment = new ApplyParchmentToSourceJar(javaHome, namesAndDocs)) {
            // Add external libraries to classpath
            if (librariesPath != null) {
                ClasspathSetup.addLibraries(librariesPath, applyParchment.javaEnv);
            }

            applyParchment.setMaxQueueDepth(queueDepth);
            applyParchment.setEnableJavadoc(enableJavadoc);
            applyParchment.apply(inputPath, outputPath);
        }
    }

    private static void printUsage(PrintStream out) {
        out.println("Arguments:");
        out.println("  --in <input-file>      Path to input source-jar");
        out.println("  --out <output-file>    Path where new source-jar will be written");
        out.println("  --names <names-file>   Path to Parchment ZIP-File or merged TSRG2-Mappings");
        out.println("  --skip-javadoc         Don't apply Javadocs");
        out.println("  --queue-depth <depth>  How many source files to wait for in parallel. 0 for synchronous processing.");
        out.println("                         0 for synchronous processing. Default is 50.");
        out.println("  --help                 Print help");
    }

    public void apply(Path inputPath, Path outputPath) throws IOException, InterruptedException {

        var sourceJarRoot = javaEnv.getEnvironment().getJarFileSystem().findFileByPath(inputPath + "!/");
        if (sourceJarRoot == null) {
            throw new FileNotFoundException("Cannot find JAR-File " + inputPath);
        }

        javaEnv.addSourcesToClasspath(sourceJarRoot);

        try (var zin = new ZipInputStream(Files.newInputStream(inputPath));
             var fout = Files.newOutputStream(outputPath);
             var asyncZout = new OrderedWorkQueue(new ZipOutputStream(fout), maxQueueDepth)) {

            for (var entry = zin.getNextEntry(); entry != null; entry = zin.getNextEntry()) {
                var originalContentBytes = zin.readAllBytes();

                var entryPath = entry.getName();
                if (entryPath.endsWith(".java")) {
                    asyncZout.submitAsync(entry, () -> {
                        return transformSource(sourceJarRoot, entryPath, originalContentBytes);
                    });
                } else {
                    asyncZout.submit(entry, originalContentBytes);
                }
            }
        }
    }

    void addJarToClassPath(Path jarFile) {
        javaEnv.addJarToClassPath(jarFile.toFile());
    }

    byte[] transformSource(VirtualFile contentRoot, String path, byte[] originalContentBytes) {
        // Instead of parsing the content we actually read from the file, we read the virtual file that is
        // visible to IntelliJ from adding the source jar. The reasoning is that IntelliJ will cache this internally
        // and reuse it when cross-referencing type-references. If we parsed from a String instead, it would parse
        // the same file twice.
        var sourceFile = contentRoot.findFileByRelativePath(path);
        if (sourceFile == null) {
            System.err.println("Can't transform " + path + " since IntelliJ doesn't see it in the source jar.");
            return originalContentBytes;
        }
        var psiFile = psiManager.findFile(sourceFile);
        if (psiFile == null) {
            System.err.println("Can't transform " + path + " since IntelliJ can't load it.");
            return originalContentBytes;
        }

        // Gather replaced ranges in the source-file with their replacement
        List<Replacement> replacements = new ArrayList<>();

        var visitor = new GatherReplacementsVisitor(namesAndDocs, enableJavadoc, replacements);
        visitor.visitElement(psiFile);

        // If no replacements were made, just stream the original content into the destination file
        if (replacements.isEmpty()) {
            return originalContentBytes;
        }

        var originalContent = psiFile.getViewProvider().getContents();
        return applyReplacements(originalContent, replacements).getBytes(StandardCharsets.UTF_8);
    }

    @NotNull
    private static String applyReplacements(CharSequence originalContent, List<Replacement> replacements) {
        // We will assemble the resulting file by iterating all ranges (replaced or not)
        // For this to work, the replacement ranges need to be in ascending order and non-overlapping
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
        return writer.toString();
    }

    /*
     * When IntelliJ crashes after an update complaining about an extension point or extension not being available,
     * check JavaPsiPlugin.xml for the name of that extension point. Then register it as it's defined in the XML
     * by hand here.
     *
     * This method is responsible for anything in the XML that is:
     * - A projectService
     * - Extension points marked as area="IDEA_PROJECT"
     * - Any extensions registered for extension points that are area="IDEA_PROJECT"
     */
    private void initProjectExtensionsAndServices(MockProject project) {
        project.registerService(PsiNameHelper.class, PsiNameHelperImpl.class);

        var projectExtensions = project.getExtensionArea();
        CoreApplicationEnvironment.registerExtensionPoint(projectExtensions, PsiTreeChangePreprocessor.EP.getName(), PsiTreeChangePreprocessor.class);
        CoreApplicationEnvironment.registerExtensionPoint(projectExtensions, PsiElementFinder.EP.getName(), PsiElementFinder.class);
        CoreApplicationEnvironment.registerExtensionPoint(projectExtensions, JvmElementProvider.EP_NAME, JvmElementProvider.class);
        PsiElementFinder.EP.getPoint(project).registerExtension(new PsiElementFinderImpl(project), rootDisposable);
    }

    /*
     * When IntelliJ crashes after an update complaining about an extension point or extension not being available,
     * check JavaPsiPlugin.xml for the name of that extension point. Then register it as it's defined in the XML
     * by hand here.
     *
     * This method is responsible for anything in the XML that is:
     * - An applicationService
     * - Extension points not marked as area="IDEA_PROJECT"
     * - Any extensions registered for extension points that are not marked area="IDEA_PROJECT"
     */
    private void initAppExtensionsAndServices(JavaCoreApplicationEnvironment appEnv) {
        // When any service or extension point is missing, check JavaPsiPlugin.xml in classpath and grab the definition
        appEnv.registerApplicationService(JavaClassSupers.class, new com.intellij.psi.impl.JavaClassSupersImpl());
        appEnv.registerApplicationService(InternalPersistentJavaLanguageLevelReaderService.class, new InternalPersistentJavaLanguageLevelReaderService.DefaultImpl());
        appEnv.registerApplicationService(TransactionGuard.class, new TransactionGuardImpl());

        var appExtensions = appEnv.getApplication().getExtensionArea();
        CoreApplicationEnvironment.registerExtensionPoint(appExtensions, PsiAugmentProvider.EP_NAME, PsiAugmentProvider.class);
        CoreApplicationEnvironment.registerExtensionPoint(appExtensions, JavaModuleSystem.EP_NAME, JavaModuleSystem.class);
        CoreApplicationEnvironment.registerExtensionPoint(appExtensions, TreeGenerator.EP_NAME, TreeGenerator.class);
        appExtensions.getExtensionPoint(TreeGenerator.EP_NAME).registerExtension(new JavaTreeGenerator(), rootDisposable);
    }

    public void setMaxQueueDepth(int maxQueueDepth) {
        this.maxQueueDepth = maxQueueDepth;
    }

    public void setEnableJavadoc(boolean enableJavadoc) {
        this.enableJavadoc = enableJavadoc;
    }

    @Override
    public void close() throws IOException {
        // Releases cached ZipFiles within IntelliJ, allowing the tempdir to be deleted
        ZipHandler.clearFileAccessorCache();
        Disposer.dispose(rootDisposable);
        Files.deleteIfExists(tempDir);
    }

}
