package net.neoforged.jst.accesstransformers;

import com.intellij.psi.PsiFile;
import net.neoforged.accesstransformer.parser.AccessTransformerFiles;
import net.neoforged.accesstransformer.parser.Target;
import net.neoforged.accesstransformer.parser.Transformation;
import net.neoforged.jst.api.Logger;
import net.neoforged.jst.api.Replacements;
import net.neoforged.jst.api.SourceTransformer;
import net.neoforged.jst.api.TransformContext;
import net.neoforged.problems.ProblemGroup;
import net.neoforged.problems.ProblemId;
import net.neoforged.problems.ProblemLocation;
import net.neoforged.problems.ProblemReporter;
import net.neoforged.problems.ProblemSeverity;
import picocli.CommandLine;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public class AccessTransformersTransformer implements SourceTransformer {

    private static final ProblemGroup PROBLEM_GROUP = ProblemGroup.create("access-transformer", "Access Transformers");
    private static final ProblemId INVALID_AT = ProblemId.create("invalid-at", "Invalid", PROBLEM_GROUP);
    private static final ProblemId MISSING_TARGET = ProblemId.create("missing-target", "Missing Target", PROBLEM_GROUP);

    private static final Pattern LINE_PATTERN = Pattern.compile("\\bline\\s+(\\d+)");
    private static final Pattern ORIGIN_PATTERN = Pattern.compile("(.*):(\\d+)$");

    @CommandLine.Option(names = "--access-transformer", required = true)
    public List<Path> atFiles;

    @CommandLine.Option(names = "--access-transformer-validation", description = "The level of validation to use for ats")
    public AccessTransformerValidation validation = AccessTransformerValidation.LOG;

    private AccessTransformerFiles ats;
    private Map<Target, Transformation> pendingATs;
    private Logger logger;
    private ProblemReporter problemReporter;
    private volatile boolean errored;

    @Override
    public void beforeRun(TransformContext context) {
        ats = new AccessTransformerFiles();
        logger = context.logger();
        problemReporter = context.problemReporter();

        for (Path path : atFiles) {
            try {
                ats.loadFromPath(path);
            } catch (Exception e) {
                logger.error("Failed to parse access transformer file %s: %s", path, e.getMessage());

                if (e.getMessage() != null) {
                    var m = LINE_PATTERN.matcher(e.getMessage());
                    if (m.matches()) {
                        int line = Integer.parseUnsignedInt(m.group(1));
                        problemReporter.report(INVALID_AT, ProblemSeverity.ERROR, ProblemLocation.ofLocationInFile(path, line), e.getMessage());
                    } else {
                        problemReporter.report(INVALID_AT, ProblemSeverity.ERROR, ProblemLocation.ofFile(path), e.getMessage());
                    }
                }

                if (e instanceof RuntimeException re) {
                    throw re;
                }
                throw new RuntimeException(e);
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

                // Report a problem for each origin of the transform
                for (String origin : transformation.origins()) {
                    var m = ORIGIN_PATTERN.matcher(origin);
                    ProblemLocation problemLocation;
                    if (!m.matches()) {
                        problemLocation = ProblemLocation.ofFile(Paths.get(origin));
                    } else {
                        var file = Paths.get(m.group(1));
                        var line = Integer.parseUnsignedInt(m.group(2));
                        // AT reports 0-based lines, we want 1-based
                        problemLocation = ProblemLocation.ofLocationInFile(file, line + 1);
                    }

                    problemReporter.report(MISSING_TARGET, ProblemSeverity.ERROR, problemLocation, "The target " + target + " does not exist.");
                }
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
