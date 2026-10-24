package net.neoforged.jst.genericswidening;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.intellij.psi.PsiFile;
import com.intellij.util.Consumer;
import com.intellij.util.containers.MultiMap;
import net.neoforged.jst.api.Replacements;
import net.neoforged.jst.api.SourceTransformer;
import net.neoforged.jst.api.TransformContext;
import picocli.CommandLine;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class GenericsWideningTransformer implements SourceTransformer {
    private static final Gson GSON = new Gson();

    @CommandLine.Option(names = "--generics-widening-data", description = "The paths to read generics widening JSON files from")
    public List<Path> paths = new ArrayList<>();

    private MultiMap<String, GenericsTarget> methods = MultiMap.empty();

    @Override
    public void beforeRun(TransformContext context) {
        methods = MultiMap.createLinkedSet();
        for (Path path : paths) {
            try {
                JsonObject json = GSON.fromJson(Files.readString(path), JsonObject.class);
                for (String clazz : json.keySet()) {
                    parseTarget(json.get(clazz), target -> methods.putValue(clazz, target));
                }
            } catch (IOException exception) {
                context.logger().error("Failed to read generics widening data file: %s", exception.getMessage());
                throw new UncheckedIOException(exception);
            }
        }
    }

    private static void parseTarget(JsonElement element, Consumer<GenericsTarget> appender) throws IOException {
        if (element.isJsonArray()) {
            for (JsonElement elem : element.getAsJsonArray()) {
                parseTarget(elem, appender);
            }
        } else if (!element.isJsonObject()) {
            throw new IOException("Invalid target entry: " + element);
        }

        JsonObject obj = element.getAsJsonObject();
        String methodName = getRequiredString(obj, "methodName");
        String methodDesc = getRequiredString(obj, "methodDescriptor");
        String paramType = getRequiredString(obj, "parameterType");
        int paramOrdinal = -1;
        if (obj.has("parameterOrdinal")) {
            JsonElement ordinalElem = obj.get("parameterOrdinal");
            if (!ordinalElem.isJsonPrimitive()) {
                throw new IOException("Invalid type for field: parameterOrdinal");
            }
            paramOrdinal = ordinalElem.getAsInt();
        }
        if (!methodDesc.contains(paramType)) {
            throw new IOException("Descriptor '" + methodDesc + "' does not contain generic parameter '" + paramType + "'");
        }
        // TODO: throw for multiple occurrences when no ordinal is provided
        appender.accept(new GenericsTarget(methodName, methodDesc, paramType, paramOrdinal));
    }

    private static String getRequiredString(JsonObject obj, String name) throws IOException {
        if (!obj.has(name)) {
            throw new IOException("Missing required field: " + name);
        }
        JsonElement element = obj.get(name);
        if (!element.isJsonPrimitive()) {
            throw new IOException("Invalid type for field: " + name);
        }
        return element.getAsString();
    }

    @Override
    public void visitFile(PsiFile psiFile, Replacements replacements) {
        new WidenGenericsVisitor(replacements, methods).visitFile(psiFile);
    }
}
