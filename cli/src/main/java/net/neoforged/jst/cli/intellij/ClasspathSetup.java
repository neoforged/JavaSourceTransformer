package net.neoforged.jst.cli.intellij;

import com.intellij.core.JavaCoreProjectEnvironment;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.io.URLUtil;
import net.neoforged.jst.api.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Properties;

public final class ClasspathSetup {
    private ClasspathSetup() {
    }

    public static void addJdkModules(Logger logger, Path jdkHome, JavaCoreProjectEnvironment javaEnv) {
        var jrtFileSystem = javaEnv.getEnvironment().getJrtFileSystem();
        if (jrtFileSystem == null) {
            throw new IllegalStateException("No JRT file system was configured");
        }

        VirtualFile jdkVfsRoot = jrtFileSystem.findFileByPath(jdkHome.toAbsolutePath() + URLUtil.JAR_SEPARATOR);
        if (jdkVfsRoot == null) {
            logger.error("Failed to load VFS-entry for JDK home " + jdkHome + ". Is it missing?");
            return;
        }

        var modulesFolder = jdkVfsRoot.findChild("modules");
        if (modulesFolder == null) {
            logger.error("VFS for JDK " + jdkHome + " doesn't have a modules subfolder");
            return;
        }

        int moduleCount = 0;
        List<String> modules = readModulesFromReleaseFile(jdkHome);
        if (modules != null) {
            for (String module : modules) {
                var moduleRoot = modulesFolder.findChild(module);
                if (moduleRoot == null || !moduleRoot.isDirectory()) {
                    logger.error("Couldn't find module " + module + " even though it was listed in the release file of JDK " + jdkHome);
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

        logger.debug("Added %s modules from %s", moduleCount, jdkHome);
    }

    public static void addLibraries(Logger logger, Path librariesPath, IntelliJEnvironmentImpl ijEnv) throws IOException {
        for (String libraryLine : Files.readAllLines(librariesPath)) {
            libraryLine = libraryLine.trim();

            // Support the fucked up list-libraries format of Vineflower command-line options
            if (libraryLine.startsWith("-e=")) {
                libraryLine = libraryLine.substring("-e=".length());
            }

            if (libraryLine.isBlank()) {
                continue;
            }

            addLibrary(logger, Paths.get(libraryLine), ijEnv);
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

    public static void addLibrary(Logger logger, Path libraryPath, IntelliJEnvironmentImpl ijEnv) {
        // Add an explicit check since PSI doesn't throw if it doesn't exist
        if (!Files.exists(libraryPath)) {
            throw new UncheckedIOException(new NoSuchFileException(libraryPath.toString()));
        }
        if (Files.isDirectory(libraryPath)) ijEnv.addFolderToClasspath(libraryPath);
        else ijEnv.addJarToClassPath(libraryPath);
        logger.debug("Added %s", libraryPath);
    }
}
