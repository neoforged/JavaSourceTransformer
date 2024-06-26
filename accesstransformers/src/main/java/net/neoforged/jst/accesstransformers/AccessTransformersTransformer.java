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

public class AccessTransformersTransformer implements SourceTransformer {

    @CommandLine.Option(names = "--access-transformer", required = true)
    public List<Path> atFiles;

    @CommandLine.Option(names = "--access-transformer-validation", description = "The level of validation to use for ats")
    public AccessTransformerValidation validation = AccessTransformerValidation.LOG;

    private AccessTransformerFiles ats;
    private Map<Target, Transformation> pendingATs;
    private Logger logger;
    private volatile boolean errored;

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

        pendingATs = new ConcurrentHashMap<>(ats.getAccessTransformers());
    }

    @Override
    public boolean afterRun(TransformContext context) {
        if (!pendingATs.isEmpty()) {
            pendingATs.forEach((target, transformation) -> {
                // ClassTarget for inner classes have a corresponding InnerClassTarget which is more obvious for users
                // so we don't log the ClassTarget as that will cause duplication
                if (target instanceof Target.ClassTarget && target.className().contains("$")) return;
                logger.error("Access transformer %s, targeting %s did not apply as its target doesn't exist", transformation, target);
            });
            errored = true;
        }

        return !(errored && validation == AccessTransformerValidation.ERROR);
    }

    @Override
    public void visitFile(PsiFile psiFile, Replacements replacements) {
        var visitor = new ApplyATsVisitor(ats, replacements, pendingATs, logger);
        visitor.visitFile(psiFile);
        if (visitor.errored) {
            errored = true;
        }
    }

    public enum AccessTransformerValidation {
        LOG,
        ERROR
    }
}
