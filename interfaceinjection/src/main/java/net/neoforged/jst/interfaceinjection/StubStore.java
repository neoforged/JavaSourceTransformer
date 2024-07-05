package net.neoforged.jst.interfaceinjection;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

class StubStore {
    private final Map<String, String> jvmToFqn = new HashMap<>();
    private final Map<String, Map<String, StubInterface>> stubs = new HashMap<>();

    public String createStub(String jvm) {
        var fqn = jvmToFqn.get(jvm);
        if (fqn != null) return fqn;

        var splitName = new ArrayList<>(Arrays.asList(jvm.split("/")));
        var name = splitName.remove(splitName.size() - 1);
        var packageName = String.join(".", splitName);
        var byInner = name.split("\\$");
        StubInterface stub = stubs.computeIfAbsent(packageName, $ -> new HashMap<>()).computeIfAbsent(byInner[0], $ -> new StubInterface(byInner[0]));
        for (int i = 1; i < byInner.length; i++) {
            stub = stub.getChildren(byInner[i]);
        }

        fqn = packageName;
        if (!fqn.isBlank()) fqn += ".";
        fqn += String.join(".", byInner);

        jvmToFqn.put(jvm, fqn);
        return fqn;
    }

    public void save(Path path) throws IOException {
        if (path.getParent() != null && !Files.isDirectory(path.getParent())) {
            Files.createDirectories(path.getParent());
        }

        try (var zos = new ZipOutputStream(Files.newOutputStream(path))) {
            for (var entry : this.stubs.entrySet()) {
                var pkg = entry.getKey();
                var stubs = entry.getValue();
                String baseDeclaration = pkg.isBlank() ? "" : ("package " + pkg + ";\n\n");
                String baseFileName = pkg.isBlank() ? "" : (pkg.replace('.', '/') + "/");
                for (StubInterface stub : stubs.values()) {
                    var builder = new StringBuilder(baseDeclaration);
                    stub.save(s -> builder.append(s).append('\n'));

                    zos.putNextEntry(new ZipEntry(baseFileName + stub.name() + ".java"));
                    zos.write(builder.toString().getBytes(StandardCharsets.UTF_8));
                    zos.closeEntry();
                }
            }
        }
    }

    public record StubInterface(String name, Map<String, StubInterface> children) {
        public StubInterface(String name) {
            this(name, new HashMap<>());
        }

        public StubInterface getChildren(String name) {
            return children.computeIfAbsent(name, StubInterface::new);
        }

        public void save(Consumer<String> consumer) {
            consumer.accept("public interface " + name + " {");
            for (StubInterface child : children.values()) {
                child.save(str -> consumer.accept("    " + str));
            }
            consumer.accept("}");
        }
    }
}
