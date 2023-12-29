package net.neoforged.jst.cli;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import net.neoforged.jst.api.FileEntry;
import net.neoforged.jst.api.FileSink;
import net.neoforged.jst.api.FileSource;
import net.neoforged.jst.api.Replacements;
import net.neoforged.jst.api.SourceTransformer;
import net.neoforged.jst.api.TransformContext;
import net.neoforged.jst.cli.intellij.ClasspathSetup;
import net.neoforged.jst.cli.intellij.IntelliJEnvironmentImpl;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

/**
 * Reference for out-of-IDE usage of the IntelliJ Java parser is from the Kotlin compiler
 * https://github.com/JetBrains/kotlin/blob/22aa9ee65f759ad21aeaeb8ad9ac0b123b2c32fe/compiler/cli/cli-base/src/org/jetbrains/kotlin/cli/jvm/compiler/KotlinCoreEnvironment.kt#L108
 */
class SourceFileProcessor implements AutoCloseable {
    private final IntelliJEnvironmentImpl ijEnv = new IntelliJEnvironmentImpl();
    private int maxQueueDepth = 50;
    private boolean enableJavadoc = true;

    public SourceFileProcessor() throws IOException {
        ijEnv.addCurrentJdkToClassPath();
    }

    public void process(FileSource source, FileSink sink, List<SourceTransformer> transformers) throws IOException {

        var context = new TransformContext(ijEnv, source, sink);

        var sourceRoot = source.createSourceRoot(VirtualFileManager.getInstance());
        ijEnv.addSourceRoot(sourceRoot);

        for (var transformer : transformers) {
            transformer.beforeRun(context);
        }

        if (sink.isOrdered()) {
            try (var stream = source.streamEntries()) {
                stream.forEach(entry -> {
                    processEntry(entry, sourceRoot, transformers, sink);
                });
            }
        } else {
            try (var asyncOut = new OrderedParallelWorkQueue(sink, maxQueueDepth);
                 var stream = source.streamEntries()) {
                stream.forEach(entry -> asyncOut.submitAsync(parallelSink -> {
                    processEntry(entry, sourceRoot, transformers, parallelSink);
                }));
            }
        }

        for (var transformer : transformers) {
            transformer.afterRun(context);
        }
    }

    private void processEntry(FileEntry entry, VirtualFile sourceRoot, List<SourceTransformer> transformers, FileSink sink) {
        try (var in = entry.openInputStream()) {
            byte[] content = in.readAllBytes();
            if (entry.hasExtension("java")) {
                content = transformSource(sourceRoot, entry.relativePath(), transformers, content);
            }
            sink.put(entry, content);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    byte[] transformSource(VirtualFile contentRoot, String path, List<SourceTransformer> transformers, byte[] originalContentBytes) {
        // Instead of parsing the content we actually read from the file, we read the virtual file that is
        // visible to IntelliJ from adding the source jar. The reasoning is that IntelliJ will cache this internally
        // and reuse it when cross-referencing type-references. If we parsed from a String instead, it would parse
        // the same file twice.
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
        var replacements = new Replacements();

        for (var transformer : transformers) {
            transformer.visitFile(psiFile, replacements);
        }

        // If no replacements were made, just stream the original content into the destination file
        if (replacements.isEmpty()) {
            return originalContentBytes;
        }

        var originalContent = psiFile.getViewProvider().getContents();
        return replacements.apply(originalContent).getBytes(StandardCharsets.UTF_8);
    }

    public void setMaxQueueDepth(int maxQueueDepth) {
        this.maxQueueDepth = maxQueueDepth;
    }

    public void setEnableJavadoc(boolean enableJavadoc) {
        this.enableJavadoc = enableJavadoc;
    }

    @Override
    public void close() throws IOException {
        ijEnv.close();
    }

    public void addLibrariesList(Path librariesList) throws IOException {
        ClasspathSetup.addLibraries(librariesList, ijEnv);
    }
}
