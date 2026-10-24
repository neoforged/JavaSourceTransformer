package net.neoforged.jst.enumextensions;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.MultiMap;
import net.neoforged.jst.api.Replacements;
import net.neoforged.jst.api.SourceTransformer;
import net.neoforged.jst.api.TransformContext;
import org.jetbrains.annotations.Nullable;
import picocli.CommandLine;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class EnumExtensionTransformer implements SourceTransformer {
    private static final Gson GSON = new Gson();

    @Nullable
    @CommandLine.Option(names = "--enum-extensions-stubs", description = "The path to a zip to save reference stubs in")
    public Path stubOut;

    @CommandLine.Option(names = "--enum-extensions-data", description = "The paths to read enum extension JSON files from")
    public List<Path> paths = new ArrayList<>();

    @Nullable
    @CommandLine.Option(names = "--enum-extensions-marker", description = "The name (binary representation) of an annotation to use as a marker for extended enum entries")
    public String annotationMarker;

    @Nullable
    @CommandLine.Option(names = "--enum-extensions-required-interface", description = "The name (binary representation) of an interface to enforce that extendeable enums implement")
    public String requiredInterface;
    
    @Nullable
    @CommandLine.Option(names = "--enum-extensions-reserved-constructor-annotation", description = "The name (binary representation) of an annotation annotating constructors that should not be used for extensions")
    public String reservedConstructorAnnotation;
    
    @Nullable
    @CommandLine.Option(names = "--enum-extensions-indexed-enum-annotation", description = "The name (binary representation) of an annotation annotating enums with an index parameter")
    public String indexedEnumAnnotation;

    private MultiMap<String, ExtensionPrototype> extensions;
    private StubStore stubs;
    private String marker;
    private String requiredInterfaceFqn;
    private String reserverCtorAnnotationFqn;
    private String indexedEnumAnnotationFqn;

    @Override
    public void beforeRun(TransformContext context) {
        extensions = new MultiMap<>();
        stubs = new StubStore(context.environment().getPsiFacade());

        if (annotationMarker != null) {
            marker = annotationMarker.replace('/', '.').replace('$', '.');
        }
        
        if (requiredInterface != null) {
            requiredInterfaceFqn = requiredInterface.replace('/', '.').replace('$', '.');
        }
        
        if (reservedConstructorAnnotation != null) {
            reserverCtorAnnotationFqn = reservedConstructorAnnotation.replace('/', '.').replace('$', '.');
        }
        
        if (indexedEnumAnnotation != null) {
            indexedEnumAnnotationFqn = indexedEnumAnnotation.replace('/', '.').replace('$', '.');
        }

        for (Path path : paths) {
            try {
                var json = GSON.fromJson(Files.readString(path), JsonObject.class);
                JsonArray entries = json.getAsJsonArray("entries");
                for (JsonElement entry : entries) {
                    JsonObject entryObj = entry.getAsJsonObject();

                    String enumName = entryObj.get("enum").getAsString();
                    String fieldName = entryObj.get("name").getAsString();
                    String ctorDesc = entryObj.get("constructor").getAsString();
                    JsonElement paramElem = entryObj.get("parameters");
                    ExtensionPrototype.EnumParameters parameters = null;
                    if (paramElem.isJsonArray()) {
                        parameters = loadConstantParameters(context, enumName, fieldName, ctorDesc, paramElem.getAsJsonArray());
                    } else if (paramElem.isJsonObject()) {
                        JsonObject obj = paramElem.getAsJsonObject();
                        String className = obj.get("class").getAsString();
                        if (obj.has("method")) {
                            String srcMethodName = obj.get("method").getAsString();
                            parameters = new ExtensionPrototype.EnumParameters.MethodReference(className, srcMethodName);
                        } else if (obj.has("field")) {
                            String srcFieldName = obj.get("field").getAsString();
                            parameters = new ExtensionPrototype.EnumParameters.FieldReference(className, srcFieldName);
                        }
                    }
                    extensions.putValue(enumName, new ExtensionPrototype(
                            fieldName,
                            ctorDesc,
                            parameters
                    ));
                    if (parameters == null) {
                        context.logger().error("Failed to read parameters for enum extension entry: %s", entryObj);
                        throw new IllegalArgumentException("Invalid parameters for enum extension entry");
                    }
                }
            } catch (IOException exception) {
                context.logger().error("Failed to read interface injection data file: %s", exception.getMessage());
                throw new UncheckedIOException(exception);
            }
        }
    }

    private static ExtensionPrototype.EnumParameters loadConstantParameters(TransformContext context, String enumName, String fieldName, String ctorDesc, JsonArray obj) {
        List<Object> params = new ArrayList<>(obj.size());
        var argTypes = MethodTypeDesc.ofDescriptor(ctorDesc).parameterArray();
        if (argTypes.length != obj.size()) {
            var message = String.format(
                    "Parameter count %s does not match argument count %s of constructor %s for field %s in enum %s",
                    obj.size(), argTypes.length, ctorDesc, fieldName, enumName
            );
            context.logger().error(message);
            throw new IllegalArgumentException(message);
        }

        int idx = 0;
        for (JsonElement element : obj) {
            ClassDesc argType = argTypes[idx];
            switch (argType.descriptorString()) {
                case "Z" -> params.add(element.getAsBoolean());
                case "C" -> {
                    String param = element.getAsString();
                    if (param.length() != 1) {
                        var message = String.format(
                                "Invalid character %s at parameter index %s for field %s in enum %s",
                                param, idx, fieldName, enumName
                        );
                        context.logger().error(message);
                        throw new IllegalArgumentException(message);
                    }
                    params.add(param.charAt(0));
                }
                case "B" -> params.add(element.getAsByte());
                case "S" -> params.add(element.getAsShort());
                case "I" -> params.add(element.getAsInt());
                case "F" -> params.add(element.getAsFloat());
                case "J" -> params.add(element.getAsLong());
                case "D" -> params.add(element.getAsDouble());
                case "Ljava/lang/String;" -> params.add(element.isJsonNull() ? null : element.getAsString());
                default -> {
                    if (!element.isJsonNull()) {
                        var message = String.format(
                                "Unsupported immediate argument type %s at parameter index %s for field %s in enum %s",
                                argType, idx, fieldName, enumName
                        );
                        context.logger().error(message);
                        throw new IllegalArgumentException(message);
                    }
                    params.add(null);
                }
            }
            idx++;
        }
        return new ExtensionPrototype.EnumParameters.Constant(params);
    }

    @Override
    public boolean afterRun(TransformContext context) {
        if (stubOut != null) {
            try {
                stubs.save(stubOut);
            } catch (IOException e) {
                context.logger().error("Failed to save stubs: %s", e.getMessage());
                throw new UncheckedIOException(e);
            }
        }

        return true;
    }

    @Override
    public void visitFile(PsiFile psiFile, Replacements replacements) {
        new EnumExtensionVisitor(replacements, extensions, stubs, marker, requiredInterfaceFqn, reserverCtorAnnotationFqn, indexedEnumAnnotationFqn).visitFile(psiFile);
    }
}
