package net.neoforged.jst.accesstransformers;

import com.intellij.psi.PsiFile;
import net.neoforged.accesstransformer.parser.AccessTransformerFiles;
import net.neoforged.jst.api.Replacements;
import net.neoforged.jst.api.SourceTransformer;
import net.neoforged.jst.api.TransformContext;
import picocli.CommandLine;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;

public class AccessTransformersTransformer implements SourceTransformer {

    @CommandLine.Option(names = "--access-transformer", required = true)
    public List<Path> atFiles;

    private AccessTransformerFiles ats;
    @Override
    public void beforeRun(TransformContext context) {
        ats = new AccessTransformerFiles();
        try {
            for (Path path : atFiles) {
                ats.loadFromPath(path);
            }
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    @Override
    public void visitFile(PsiFile psiFile, Replacements replacements) {
        new ApplyATsVisitor(ats, replacements).visitFile(psiFile);
    }
}
