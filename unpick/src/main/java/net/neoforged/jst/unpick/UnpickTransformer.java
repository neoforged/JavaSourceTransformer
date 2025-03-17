package net.neoforged.jst.unpick;

import com.intellij.psi.PsiFile;
import net.earthcomputer.unpickv3parser.UnpickV3Reader;
import net.earthcomputer.unpickv3parser.tree.GroupDefinition;
import net.earthcomputer.unpickv3parser.tree.TargetField;
import net.earthcomputer.unpickv3parser.tree.TargetMethod;
import net.earthcomputer.unpickv3parser.tree.UnpickV3Visitor;
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
        var groups = new HashMap<UnpickCollection.TypedKey, GroupDefinition>();
        var fields = new ArrayList<TargetField>();
        var methods = new ArrayList<TargetMethod>();

        for (Path path : paths) {
            try (var reader = Files.newBufferedReader(path)) {
                new UnpickV3Reader(reader).accept(new UnpickV3Visitor() {
                    @Override
                    public void visitGroupDefinition(GroupDefinition groupDefinition) {
                        groups.merge(new UnpickCollection.TypedKey(groupDefinition.dataType, groupDefinition.scope, groupDefinition.name), groupDefinition, UnpickTransformer.this::merge);
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

    private GroupDefinition merge(GroupDefinition first, GroupDefinition second) {
        // TODO - validate they can be merged
        first.constants.addAll(second.constants);
        return first;
    }
}
