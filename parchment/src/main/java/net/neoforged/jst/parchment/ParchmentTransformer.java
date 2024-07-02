package net.neoforged.jst.parchment;

import com.intellij.psi.PsiFile;
import net.neoforged.jst.api.Replacements;
import net.neoforged.jst.api.SourceTransformer;
import net.neoforged.jst.api.TransformContext;
import net.neoforged.jst.parchment.namesanddocs.NameAndDocSourceLoader;
import net.neoforged.jst.parchment.namesanddocs.NamesAndDocsDatabase;
import picocli.CommandLine;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.function.UnaryOperator;

public class ParchmentTransformer implements SourceTransformer {
    @CommandLine.Option(names = "--parchment-mappings", required = true, description = "The location of the Parchment mappings file")
    public Path mappingsPath;

    @CommandLine.Option(
            names = "--parchment-javadoc",
            description = "Whether Parchment javadocs should be applied",
            negatable = true,
            fallbackValue = "true"
    )
    public boolean enableJavadoc = true;

    @CommandLine.Option(names = "--parchment-conflict-prefix", description = "Apply the prefix specified if a Parchment parameter name conflicts with existing variable names")
    public String conflictPrefix;

    private NamesAndDocsDatabase namesAndDocs;
    private UnaryOperator<String> conflictResolver;

    @Override
    public void beforeRun(TransformContext context) {
        if (conflictPrefix != null) {
            if (conflictPrefix.isBlank()) {
                throw new IllegalArgumentException("Parchment conflict prefix cannot be blank");
            }

            if (Character.isLetterOrDigit(conflictPrefix.charAt(conflictPrefix.length() - 1))) {
                conflictResolver = p -> conflictPrefix + capitalize(p);
            } else {
                conflictResolver = p -> conflictPrefix + p;
            }
        }

        System.out.println("Loading mapping file " + mappingsPath);
        try {
            namesAndDocs = NameAndDocSourceLoader.load(mappingsPath);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void visitFile(PsiFile psiFile, Replacements replacements) {
        var visitor = new GatherReplacementsVisitor(namesAndDocs, enableJavadoc, conflictResolver, replacements);
        visitor.visitElement(psiFile);
    }

    private static String capitalize(String str) {
        if (str.length() == 1) {
            return str.toUpperCase(Locale.ROOT);
        }
        return Character.toUpperCase(str.charAt(0)) + str.substring(1);
    }
}
