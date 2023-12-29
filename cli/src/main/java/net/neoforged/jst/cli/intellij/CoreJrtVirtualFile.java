package net.neoforged.jst.cli.intellij;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.util.io.URLUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

class CoreJrtVirtualFile extends VirtualFile {

    private final CoreJrtFileSystem virtualFileSystem;
    private final String jdkHomePath;
    private final Path path;
    private final CoreJrtVirtualFile parent;

    public CoreJrtVirtualFile(CoreJrtFileSystem virtualFileSystem, String jdkHomePath, Path path, CoreJrtVirtualFile parent) {
        this.virtualFileSystem = virtualFileSystem;
        this.jdkHomePath = jdkHomePath;
        this.path = path;
        this.parent = parent;
    }

    private BasicFileAttributes getAttributes() {
        try {
            return Files.readAttributes(path, BasicFileAttributes.class);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public @NotNull VirtualFileSystem getFileSystem() {
        return virtualFileSystem;
    }

    @Override
    public @NotNull String getName() {
        return path.getFileName().toString();
    }

    @Override
    public @NonNls @NotNull String getPath() {
        return FileUtil.toSystemIndependentName(jdkHomePath + URLUtil.JAR_SEPARATOR + path);
    }

    @Override
    public boolean isWritable() {
        return false;
    }

    @Override
    public boolean isDirectory() {
        return Files.isDirectory(path);
    }

    @Override
    public boolean isValid() {
        return true;
    }

    @Override
    public VirtualFile getParent() {
        return parent;
    }

    @Nullable
    private VirtualFile[] myChildren = null;

    private final ReadWriteLock rwl = new ReentrantReadWriteLock();

    @Override
    public VirtualFile[] getChildren() {
        rwl.readLock().lock();
        try {
            if (myChildren != null) {
                return myChildren;
            }
        } finally {
            rwl.readLock().unlock();
        }

        rwl.writeLock().lock();
        try {
            if (myChildren == null) {
                myChildren = computeChildren();
            }
            return myChildren;
        } finally {
            rwl.writeLock().unlock();
        }
    }

    private VirtualFile[] computeChildren() {
        List<VirtualFile> paths = new ArrayList<>();
        try (var dirStream = Files.newDirectoryStream(path)) {
            for (Path childPath : dirStream) {
                paths.add(new CoreJrtVirtualFile(virtualFileSystem, jdkHomePath, childPath, this));
            }
        } catch (IOException ignored) {
        }

        if (paths.isEmpty()) {
            return EMPTY_ARRAY;
        } else {
            return paths.toArray(new VirtualFile[0]);
        }
    }

    @Override
    public @NotNull OutputStream getOutputStream(Object requestor, long newModificationStamp, long newTimeStamp) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public byte @NotNull [] contentsToByteArray() throws IOException {
        return Files.readAllBytes(path);
    }

    @Override
    public long getTimeStamp() {
        return getAttributes().lastModifiedTime().toMillis();
    }

    @Override
    public long getLength() {
        return getAttributes().size();
    }

    @Override
    public void refresh(boolean asynchronous, boolean recursive, @Nullable Runnable postRunnable) {
    }

    @Override
    public @NotNull InputStream getInputStream() throws IOException {
        return VfsUtilCore.inputStreamSkippingBOM(new BufferedInputStream(Files.newInputStream(path)), this);
    }

    @Override
    public long getModificationStamp() {
        return 0;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof CoreJrtVirtualFile jrtVf &&
                path == jrtVf.path
                && virtualFileSystem == jrtVf.virtualFileSystem;
    }

    @Override
    public int hashCode() {
        return path.hashCode();
    }
}
