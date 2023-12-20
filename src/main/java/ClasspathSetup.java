import com.intellij.core.JavaCoreProjectEnvironment;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.io.URLUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

public final class ClasspathSetup {
    private ClasspathSetup() {
    }

    public static void addJdkModules(Path jdkHome, JavaCoreProjectEnvironment javaEnv) {
        var jrtFileSystem = javaEnv.getEnvironment().getJrtFileSystem();

        VirtualFile jdkVfsRoot = jrtFileSystem.findFileByPath(jdkHome.toAbsolutePath() + URLUtil.JAR_SEPARATOR);
        if (jdkVfsRoot == null) {
            System.err.println("Failed to load VFS-entry for JDK home " + jdkHome + ". Is it missing?");
            return;
        }

        var modulesFolder = jdkVfsRoot.findChild("modules");
        if (modulesFolder == null) {
            System.err.println("VFS for JDK " + jdkHome + " doesn't have a modules subfolder");
            return;
        }

        int moduleCount = 0;
        List<String> modules = readModulesFromReleaseFile(jdkHome);
        if (modules != null) {
            for (String module : modules) {
                var moduleRoot = modulesFolder.findChild(module);
                if (moduleRoot == null || !moduleRoot.isDirectory()) {
                    System.err.println("Couldn't find module " + module + " even though it was listed in the release file of JDK " + jdkHome);
                } else {
                    javaEnv.addSourcesToClasspath(moduleRoot);
                    moduleCount++;
                }
            }
        } else {

            for (VirtualFile jrtChild : modulesFolder.getChildren()) {
                if (jrtChild.isDirectory()) {
                    javaEnv.addSourcesToClasspath(jrtChild);
                    moduleCount++;
                }
            }
        }

        System.out.println("Added " + moduleCount + " modules from " + jdkHome);
    }

    public static void addLibraries(Path librariesPath, JavaCoreProjectEnvironment javaEnv) throws IOException {
        var libraryFiles = Files.readAllLines(librariesPath)
                .stream()
                .filter(l -> l.startsWith("-e="))
                .map(l -> l.substring(3))
                .map(File::new)
                .toList();

        for (var libraryFile : libraryFiles) {
            if (!libraryFile.exists()) {
                throw new UncheckedIOException(new FileNotFoundException(libraryFile.getAbsolutePath()));
            }
            javaEnv.addJarToClassPath(libraryFile);
            System.out.println("Added " + libraryFile);
        }
    }

    /**
     * Reads the "release" file found at the root of normal JDKs
     */
    private static @Nullable List<String> readModulesFromReleaseFile(@NotNull Path jrtBaseDir) {
        try (InputStream stream = Files.newInputStream(jrtBaseDir.resolve("release"))) {
            Properties p = new Properties();
            p.load(stream);
            String modules = p.getProperty("MODULES");
            if (modules != null) {
                return StringUtil.split(StringUtil.unquoteString(modules), " ");
            }
        } catch (IOException | IllegalArgumentException e) {
            return null;
        }
        return null;
    }
}
