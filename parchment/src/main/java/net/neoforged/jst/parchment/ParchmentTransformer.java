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

public class ParchmentTransformer implements SourceTransformer {
    @CommandLine.Option(names = "--parchment-mappings", required = true)
    public Path mappingsPath;

    @CommandLine.Option(names = "--parchment-javadoc")
    public boolean enableJavadoc = true;

    private NamesAndDocsDatabase namesAndDocs;

    @Override
    public void beforeRun(TransformContext context) {
        System.out.println("Loading mapping file " + mappingsPath);
        try {
            namesAndDocs = NameAndDocSourceLoader.load(mappingsPath);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void visitFile(PsiFile psiFile, Replacements replacements) {

        var visitor = new GatherReplacementsVisitor(namesAndDocs, enableJavadoc, replacements);
        visitor.visitElement(psiFile);

    }

}
