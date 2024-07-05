package net.neoforged.jst.interfaceinjection;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.MultiMap;
import net.neoforged.jst.api.Replacements;
import net.neoforged.jst.api.SourceTransformer;
import net.neoforged.jst.api.TransformContext;
import picocli.CommandLine;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class InterfaceInjectionTransformer implements SourceTransformer {
    private static final Gson GSON = new Gson();

    @CommandLine.Option(names = "--interface-injection-stub-location", required = true, description = "The path to save interface stubs to")
    public Path stubOut;

    @CommandLine.Option(names = "--interface-injection-marker", description = "The name of an annotation to use as a marker for injected interfaces")
    public String annotationMarker;

    @CommandLine.Option(names = "--interface-injection-data", description = "The paths to read interface injection JSON files from")
    public List<Path> paths;

    private MultiMap<String, String> interfaces;
    private StubStore stubs;
    private String marker;

    @Override
    public void beforeRun(TransformContext context) {
        interfaces = new MultiMap<>();
        stubs = new StubStore(context.logger(), context.environment().getPsiFacade());

        if (annotationMarker != null) {
            marker = annotationMarker.replace('/', '.').replace('$', '.');
        }

        for (Path path : paths) {
            try {
                var json = GSON.fromJson(Files.readString(path), JsonObject.class);
                for (String clazz : json.keySet()) {
                    var entry = json.get(clazz);
                    if (entry.isJsonArray()) {
                        entry.getAsJsonArray().forEach(el -> interfaces.putValue(clazz, el.getAsString()));
                    } else {
                        interfaces.putValue(clazz, entry.getAsString());
                    }
                }
            } catch (IOException exception) {
                context.logger().error("Failed to read interface injection data file: %s", exception.getMessage());
                throw new UncheckedIOException(exception);
            }
        }
    }

    @Override
    public boolean afterRun(TransformContext context) {
        try {
            stubs.save(stubOut);
        } catch (IOException e) {
            context.logger().error("Failed to save stubs: %s", e.getMessage());
            throw new UncheckedIOException(e);
        }

        return true;
    }

    @Override
    public void visitFile(PsiFile psiFile, Replacements replacements) {
        new InjectInterfacesVisitor(replacements, interfaces, stubs, marker).visitFile(psiFile);
    }
}
