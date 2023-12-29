package net.neoforged.jst.cli.intellij;

import com.intellij.core.CoreApplicationEnvironment;
import com.intellij.core.JavaCoreApplicationEnvironment;
import com.intellij.core.JavaCoreProjectEnvironment;
import com.intellij.lang.java.JavaLanguage;
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
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiNameHelper;
import com.intellij.psi.augment.PsiAugmentProvider;
import com.intellij.psi.impl.JavaClassSupersImpl;
import com.intellij.psi.impl.PsiElementFinderImpl;
import com.intellij.psi.impl.PsiNameHelperImpl;
import com.intellij.psi.impl.PsiTreeChangePreprocessor;
import com.intellij.psi.impl.source.tree.JavaTreeGenerator;
import com.intellij.psi.impl.source.tree.TreeGenerator;
import com.intellij.psi.util.JavaClassSupers;
import net.neoforged.jst.api.IntelliJEnvironment;
import org.jetbrains.annotations.VisibleForTesting;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

public class IntelliJEnvironmentImpl implements IntelliJEnvironment, AutoCloseable {

    private final Disposable rootDisposable;
    private final Path tempDir;
    private final MockProject project;
    private final JavaCoreProjectEnvironment javaEnv;
    private final PsiManager psiManager;

    public IntelliJEnvironmentImpl() throws IOException {
        System.setProperty("java.awt.headless", "true");

        tempDir = Files.createTempDirectory("jst");
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

        project = javaEnv.getProject();

        initProjectExtensionsAndServices(project);

        LanguageLevelProjectExtension.getInstance(project).setLanguageLevel(LanguageLevel.JDK_17);

        psiManager = PsiManager.getInstance(project);
    }

    @Override
    public PsiManager getPsiManager() {
        return psiManager;
    }

    @Override
    public CoreApplicationEnvironment getAppEnv() {
        return javaEnv.getEnvironment();
    }

    @Override
    public JavaCoreProjectEnvironment getProjectEnv() {
        return javaEnv;
    }

    public void addJarToClassPath(Path jarFile) {
        javaEnv.addJarToClassPath(jarFile.toFile());
    }

    public void addFolderToClasspath(Path folder) {
        var localFile = getAppEnv().getLocalFileSystem().findFileByNioFile(folder);
        Objects.requireNonNull(localFile);
        javaEnv.addSourcesToClasspath(localFile);
    }

    public void addSourceRoot(VirtualFile sourceRoot) {
        javaEnv.addSourcesToClasspath(sourceRoot);
    }

    public void addCurrentJdkToClassPath() {
        // Add the Java Runtime we are currently running in
        var javaHome = Paths.get(System.getProperty("java.home"));
        ClasspathSetup.addJdkModules(javaHome, javaEnv);
    }

    @Override
    public void close() throws IOException {
        // Releases cached ZipFiles within IntelliJ, allowing the tempdir to be deleted
        ZipHandler.clearFileAccessorCache();
        Disposer.dispose(rootDisposable);
        Files.deleteIfExists(tempDir);
    }

    @VisibleForTesting
    public PsiFile parseFileFromMemory(String filename, String fileContent) {
        var fileFactory = PsiFileFactory.getInstance(project);
        return fileFactory.createFileFromText(filename, JavaLanguage.INSTANCE, fileContent);
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
        appEnv.registerApplicationService(JavaClassSupers.class, new JavaClassSupersImpl());
        appEnv.registerApplicationService(InternalPersistentJavaLanguageLevelReaderService.class, new InternalPersistentJavaLanguageLevelReaderService.DefaultImpl());
        appEnv.registerApplicationService(TransactionGuard.class, new TransactionGuardImpl());

        var appExtensions = appEnv.getApplication().getExtensionArea();
        CoreApplicationEnvironment.registerExtensionPoint(appExtensions, PsiAugmentProvider.EP_NAME, PsiAugmentProvider.class);
        CoreApplicationEnvironment.registerExtensionPoint(appExtensions, JavaModuleSystem.EP_NAME, JavaModuleSystem.class);
        CoreApplicationEnvironment.registerExtensionPoint(appExtensions, TreeGenerator.EP_NAME, TreeGenerator.class);
        appExtensions.getExtensionPoint(TreeGenerator.EP_NAME).registerExtension(new JavaTreeGenerator(), rootDisposable);
    }

}
