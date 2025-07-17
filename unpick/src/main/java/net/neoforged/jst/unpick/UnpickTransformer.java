package net.neoforged.jst.unpick;

import com.intellij.psi.PsiFile;
import daomephsta.unpick.constantmappers.datadriven.parser.v3.UnpickV3Reader;
import daomephsta.unpick.constantmappers.datadriven.tree.GroupDefinition;
import daomephsta.unpick.constantmappers.datadriven.tree.TargetField;
import daomephsta.unpick.constantmappers.datadriven.tree.TargetMethod;
import daomephsta.unpick.constantmappers.datadriven.tree.UnpickV3Visitor;
import net.neoforged.jst.api.Replacements;
import net.neoforged.jst.api.SourceTransformer;
import net.neoforged.jst.api.TransformContext;
import picocli.CommandLine;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class UnpickTransformer implements SourceTransformer {
    @CommandLine.Option(names = "--unpick-data", description = "The paths to read unpick definition files from")
    public List<Path> paths = new ArrayList<>();

    private UnpickCollection collection;

    @Override
    public void beforeRun(TransformContext context) {
        var groups = new HashMap<UnpickCollection.TypedKey, List<GroupDefinition>>();
        var fields = new ArrayList<TargetField>();
        var methods = new ArrayList<TargetMethod>();

        for (Path path : paths) {
            try (var reader = Files.newBufferedReader(path)) {
                new UnpickV3Reader(reader).accept(new UnpickV3Visitor() {
                    @Override
                    public void visitGroupDefinition(GroupDefinition groupDefinition) {
                        groups.computeIfAbsent(new UnpickCollection.TypedKey(groupDefinition.dataType(), groupDefinition.scopes(), groupDefinition.name()), k -> new ArrayList<>())
                                .add(groupDefinition);
                    }

                    @Override
                    public void visitTargetField(TargetField targetField) {
                        fields.add(targetField);
                    }

                    @Override
                    public void visitTargetMethod(TargetMethod targetMethod) {
                        methods.add(targetMethod);
                    }
                });
            } catch (IOException exception) {
                context.logger().error("Failed to read unpick definition file: %s", exception.getMessage());
                throw new UncheckedIOException(exception);
            }
        }

        this.collection = new UnpickCollection(context, groups, fields, methods);
    }

    @Override
    public void visitFile(PsiFile psiFile, Replacements replacements) {
        new UnpickVisitor(psiFile, collection, replacements).visitFile(psiFile);
    }
}
