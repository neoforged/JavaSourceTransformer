package net.neoforged.jst.cli.intellij;

import com.intellij.openapi.vfs.DeprecatedVirtualFileSystem;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ConcurrentFactoryMap;
import com.intellij.util.io.URLUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.util.Map;

class CoreJrtFileSystem extends DeprecatedVirtualFileSystem {

    private final Map<String, CoreJrtVirtualFile> roots = ConcurrentFactoryMap.createMap(jdkHomePath -> {
        var jdkHome = new File(jdkHomePath);
        var jrtFsJar = getJrtFsJar(jdkHome);
        if (!jrtFsJar.exists()) {
            return null;
        }
        var rootUri = URI.create(StandardFileSystems.JRT_PROTOCOL + ":/");
            /*
              The ClassLoader, that was used to load JRT FS Provider actually lives as long as current thread due to ThreadLocal leak in jrt-fs,
              See https://bugs.openjdk.java.net/browse/JDK-8260621
              So that cache allows us to avoid creating too many classloaders for same JDK and reduce severity of that leak
            */
        // If the runtime JDK is set to 9+ it has JrtFileSystemProvider,
        // but to load proper jrt-fs (one that is pointed by jdkHome) we should provide "java.home" path
        FileSystem fileSystem;
        try {
            fileSystem = FileSystems.newFileSystem(rootUri, Map.of("java.home", jdkHome.getAbsolutePath()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        return new CoreJrtVirtualFile(this, jdkHomePath, fileSystem.getPath(""), null);
    });

    @Override
    public @NonNls @NotNull String getProtocol() {
        return StandardFileSystems.JRT_PROTOCOL;
    }

    @Override
    public @Nullable VirtualFile findFileByPath(@NotNull @NonNls String path) {
        var splitPath = splitPath(path);
        var jdkHomePath = splitPath.jdkHome;
        var pathInImage = splitPath.pathInImage;
        var root = roots.get(jdkHomePath);
        if (root == null) {
            return null;
        }

        if (pathInImage.isEmpty()) return root;

        return root.findFileByRelativePath(pathInImage);
    }

    @Override
    public void refresh(boolean asynchronous) {
    }

    @Override
    public @Nullable VirtualFile refreshAndFindFileByPath(@NotNull String path) {
        return findFileByPath(path);
    }

    private void clearRoots() {
        roots.clear();
    }

    private static File getJrtFsJar(File jdkHome) {
        return new File(jdkHome, "lib/jrt-fs.jar");
    }

    static boolean isModularJdk(File jdkHome) {
        return getJrtFsJar(jdkHome).exists();
    }

    private static JdkImagePath splitPath(String path) {
        var separator = path.indexOf(URLUtil.JAR_SEPARATOR);
        if (separator < 0) {
            throw new IllegalArgumentException("Path in CoreJrtFileSystem must contain a separator: " + path);
        }
        var localPath = path.substring(0, separator);
        var pathInJar = path.substring(separator + URLUtil.JAR_SEPARATOR.length());
        return new JdkImagePath(localPath, pathInJar);
    }

    record JdkImagePath(String jdkHome, String pathInImage) {
    }
}
