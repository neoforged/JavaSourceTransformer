package net.neoforged.jst.cli;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import net.neoforged.jst.api.FileEntry;
import net.neoforged.jst.api.FileSink;
import net.neoforged.jst.api.FileSource;
import net.neoforged.jst.api.Logger;
import net.neoforged.jst.api.Replacement;
import net.neoforged.jst.api.Replacements;
import net.neoforged.jst.api.SourceTransformer;
import net.neoforged.jst.api.TransformContext;
import net.neoforged.jst.cli.intellij.ClasspathSetup;
import net.neoforged.jst.cli.intellij.IntelliJEnvironmentImpl;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Reference for out-of-IDE usage of the IntelliJ Java parser is from the Kotlin compiler
 * https://github.com/JetBrains/kotlin/blob/22aa9ee65f759ad21aeaeb8ad9ac0b123b2c32fe/compiler/cli/cli-base/src/org/jetbrains/kotlin/cli/jvm/compiler/KotlinCoreEnvironment.kt#L108
 */
class SourceFileProcessor implements AutoCloseable {
    private final IntelliJEnvironmentImpl ijEnv;
    private int maxQueueDepth = 50;
    private final Logger logger;

    private final List<String> ignoredPrefixes = new ArrayList<>();

    public SourceFileProcessor(Logger logger) throws IOException {
        this.logger = logger;
        ijEnv = new IntelliJEnvironmentImpl(logger);
        ijEnv.addCurrentJdkToClassPath();
    }

    public boolean process(FileSource source, FileSink sink, List<SourceTransformer> transformers) throws IOException {
        if (source.canHaveMultipleEntries() && !sink.canHaveMultipleEntries()) {
            throw new IllegalStateException("Cannot have an input with possibly more than one file when the output is a single file.");
        }

        var context = new TransformContext(ijEnv, source, sink, logger);

        var sourceRoot = source.createSourceRoot(VirtualFileManager.getInstance());
        ijEnv.addSourceRoot(sourceRoot);

        for (var transformer : transformers) {
            transformer.beforeRun(context);
        }

        if (source.isOrdered() && sink.isOrdered()) {
            try (var stream = source.streamEntries()) {
                stream.forEach(entry -> {
                    try {
                        processEntry(entry, sourceRoot, transformers, sink);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
            }
        } else {
            var success = new AtomicBoolean(true);
            try (var asyncOut = new OrderedParallelWorkQueue(sink, maxQueueDepth);
                 var stream = source.streamEntries()) {
                stream.forEach(entry -> asyncOut.submitAsync(parallelSink -> {
                    try {
                        if (!processEntry(entry, sourceRoot, transformers, parallelSink)) {
                            success.set(false);
                        }
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }));
            }
            if (!success.get()) {
                return false;
            }
        }

        boolean isOk = true;
        for (var transformer : transformers) {
            isOk = isOk && transformer.afterRun(context);
        }

        return isOk;
    }

    private boolean processEntry(FileEntry entry, VirtualFile sourceRoot, List<SourceTransformer> transformers, FileSink sink) throws IOException {
        if (entry.directory()) {
            sink.putDirectory(entry.relativePath());
            return true;
        }
        
        boolean[] success = {true};

        try (var in = entry.openInputStream()) {
            byte[] content = in.readAllBytes();
            var lastModified = entry.lastModified();

            if (!isIgnored(entry.relativePath()) && !transformers.isEmpty() && entry.hasExtension("java")) {
                var orgContent = content;
                content = transformSource(sourceRoot, entry, transformers, content, success);
                if (!success[0]) {
                    return false;
                }
                if (orgContent != content) {
                    lastModified = FileTime.from(Instant.now());
                }
            }
            sink.putFile(entry.relativePath(), lastModified, content);
        }
        return true;
    }

    private boolean isIgnored(String relativePath) {
        for (String ignoredPrefix : ignoredPrefixes) {
            if (relativePath.startsWith(ignoredPrefix)) {
                return true;
            }
        }
        return false;
    }

    private byte[] transformSource(VirtualFile contentRoot, FileEntry entry, List<SourceTransformer> transformers, byte[] originalContentBytes, boolean[] successOut) {
        // Instead of parsing the content we actually read from the file, we read the virtual file that is
        // visible to IntelliJ from adding the source jar. The reasoning is that IntelliJ will cache this internally
        // and reuse it when cross-referencing type-references. If we parsed from a String instead, it would parse
        // the same file twice.
        var path = entry.relativePath();
        var sourceFile = contentRoot.findFileByRelativePath(path);
        if (sourceFile == null) {
            System.err.println("Can't transform " + path + " since IntelliJ doesn't see it in the source jar.");
            return originalContentBytes;
        }
        var psiFile = ijEnv.getPsiManager().findFile(sourceFile);
        if (psiFile == null) {
            System.err.println("Can't transform " + path + " since IntelliJ can't load it.");
            return originalContentBytes;
        }

        // Gather replaced ranges in the source-file with their replacement
        List<Replacement> replacementsList = new ArrayList<>();
        var replacements = new Replacements(replacementsList);

        for (var transformer : transformers) {
            transformer.visitFile(psiFile, replacements);
        }

        var readOnlyReplacements = Collections.unmodifiableList(replacementsList);
        boolean success = true;
        for (var transformer : transformers) {
            success = success && transformer.beforeReplacement(entry, readOnlyReplacements);
        }
        
        successOut[0] = success;

        // If no replacements were made, just stream the original content into the destination file
        if (!success || replacements.isEmpty()) {
            return originalContentBytes;
        }

        var originalContent = psiFile.getViewProvider().getContents();
        return replacements.apply(originalContent).getBytes(StandardCharsets.UTF_8);
    }

    public void setMaxQueueDepth(int maxQueueDepth) {
        this.maxQueueDepth = maxQueueDepth;
    }

    public void addLibrariesList(Path librariesList) throws IOException {
        ClasspathSetup.addLibraries(logger, librariesList, ijEnv);
    }

    public void addLibrary(Path library) {
        ClasspathSetup.addLibrary(logger, library, ijEnv);
    }

    public void addIgnoredPrefix(String ignoredPrefix) {
        System.out.println("Not transforming entries starting with " + ignoredPrefix);
        this.ignoredPrefixes.add(ignoredPrefix);
    }

    @Override
    public void close() throws IOException {
        ijEnv.close();
    }
}
