package net.neoforged.jst.accesstransformers;

import com.intellij.psi.PsiFile;
import net.neoforged.accesstransformer.api.AccessTransformer;
import net.neoforged.accesstransformer.api.AccessTransformerEngine;
import net.neoforged.accesstransformer.parser.AccessTransformerList;
import net.neoforged.jst.api.Replacements;
import net.neoforged.jst.api.SourceTransformer;
import net.neoforged.jst.api.TransformContext;
import picocli.CommandLine;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class ATsTransformer implements SourceTransformer {

    @CommandLine.Option(names = "--access-transformer-files", required = true)
    public List<Path> atFiles;

    private Map<String, List<AccessTransformer>> targets;
    @Override
    public void beforeRun(TransformContext context) {
        final var engine = AccessTransformerEngine.newEngine();
        try {
            for (Path path : atFiles) {
                engine.loadATFromPath(path);
            }
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }

        targets = engine.getAccessTransformers();
    }

    @Override
    public void visitFile(PsiFile psiFile, Replacements replacements) {
        new ApplyATsVisitor(targets, replacements).visitFile(psiFile);
    }
}
