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
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class AccessTransformersTransformer implements SourceTransformer {

    @CommandLine.Option(names = "--access-transformer", required = true)
    public List<Path> atFiles;

    @CommandLine.Option(names = "--access-transformer-validation", description = "The level of validation to use for ats")
    public AccessTransformerValidation validation = AccessTransformerValidation.LOG;

    // TODO: as is, either this or the normal option is required
    @CommandLine.Option(names = "--access-transformer-no-validation")
    public List<Path> atFilesWithoutValidation;

    private AccessTransformerFiles ats;
    private Map<Target, Transformation> pendingATs;
    private Set<String> sourcesWithoutValidation;
    private Logger logger;
    private volatile boolean errored;

    @Override
    public void beforeRun(TransformContext context) {
        ats = new AccessTransformerFiles();
        logger = context.logger();
        sourcesWithoutValidation = new HashSet<>();

        for (Path path : atFiles) {
            try {
                ats.loadFromPath(path);
            } catch (IOException ex) {
                logger.error("Failed to parse access transformer file %s: %s", path, ex.getMessage());
                throw new UncheckedIOException(ex);
            }
        }

        for (Path path : atFilesWithoutValidation) {
            var sourceName = path.toAbsolutePath().toString();
            try (Reader reader = Files.newBufferedReader(path)) {
                ats.loadAT(reader, sourceName);
            } catch (IOException ex) {
                logger.error("Failed to parse access transformer file %s: %s", path, ex.getMessage());
                throw new UncheckedIOException(ex);
            }
            sourcesWithoutValidation.add(sourceName);
        }

        pendingATs = new ConcurrentHashMap<>(ats.getAccessTransformers());
    }

    @Override
    public boolean afterRun(TransformContext context) {
        pendingATs.forEach((target, transformation) -> {
            // Ignore targets that could not be applied,
            // but are only referenced in AT files for which we don't want to perform any validation
            boolean ignore = transformation.origins()
                    .stream()
                    .allMatch(origin -> {
                        var colonPos = origin.lastIndexOf(':');
                        var sourceName = colonPos == -1 ? origin : origin.substring(0, colonPos);
                        return sourcesWithoutValidation.contains(sourceName);
                    });
            if (ignore) {
                return;
            }
            // ClassTarget for inner classes have a corresponding InnerClassTarget which is more obvious for users
            // so we don't log the ClassTarget as that will cause duplication
            if (target instanceof Target.ClassTarget && target.className().contains("$")) {
                return;
            }

            logger.error("Access transformer %s, targeting %s did not apply as its target doesn't exist", transformation, target);
            errored = true;
        });

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
