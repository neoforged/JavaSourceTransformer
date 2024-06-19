package net.neoforged.jst.accesstransformers;

import com.intellij.psi.PsiFile;
import net.neoforged.accesstransformer.parser.AccessTransformerFiles;
import net.neoforged.accesstransformer.parser.Target;
import net.neoforged.accesstransformer.parser.Transformation;
import net.neoforged.jst.api.Logger;
import net.neoforged.jst.api.Replacements;
import net.neoforged.jst.api.SourceTransformer;
import net.neoforged.jst.api.TransformContext;
import picocli.CommandLine;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class AccessTransformersTransformer implements SourceTransformer {

    @CommandLine.Option(names = "--access-transformer", required = true)
    public List<Path> atFiles;

    @CommandLine.Option(names = "--access-transformer-validation", description = "The level of validation to use for ats")
    public AccessTransformerValidation validation = AccessTransformerValidation.LOG;

    private AccessTransformerFiles ats;
    private Map<Target, Transformation> atCopy;
    private Logger logger;
    private final AtomicBoolean errored = new AtomicBoolean();

    @Override
    public void beforeRun(TransformContext context) {
        ats = new AccessTransformerFiles();
        logger = context.logger();

        for (Path path : atFiles) {
            try {
                ats.loadFromPath(path);
            } catch (IOException ex) {
                logger.error("Failed to parse access transformer file %s: %s", path, ex.getMessage());
                throw new UncheckedIOException(ex);
            }
        }

        atCopy = new ConcurrentHashMap<>(ats.getAccessTransformers());
    }

    @Override
    public boolean afterRun(TransformContext context) {
        if (!atCopy.isEmpty()) {
            atCopy.forEach((target, transformation) -> logger.error("Access transformer %s, targeting %s did not apply as its target doesn't exist", transformation, target));
            errored.set(true);
        }

        return !(errored.get() && validation == AccessTransformerValidation.ERROR);
    }

    @Override
    public void visitFile(PsiFile psiFile, Replacements replacements) {
        var visitor = new ApplyATsVisitor(ats, replacements, atCopy, logger);
        visitor.visitFile(psiFile);
        if (visitor.errored) {
            errored.set(true);
        }
    }

    public enum AccessTransformerValidation {
        LOG,
        ERROR
    }
}
