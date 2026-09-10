package net.neoforged.jst.enumextensions;

import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.search.GlobalSearchScope;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * When adding enum entries, they may reference methods or fields in mod code. We create stubs to recompile against to
 * avoid circular dependencies
 */
class StubStore {
    private final JavaPsiFacade facade;
    private final Map<String, String> jvmToFqn = new HashMap<>();
    private final Map<String, StubClass> stubsByFqn = new HashMap<>();
    private final Map<String, Map<String, StubClass>> stubs = new HashMap<>();

    StubStore(JavaPsiFacade facade) {
        this.facade = facade;
    }

    public synchronized String createStub(String jvm, String member, boolean isMethod) {
        var fqn = jvmToFqn.get(jvm);
        if (fqn != null) {
            var stub = stubsByFqn.get(fqn);
            if (stub != null) {
                addToStub(member, isMethod, stub);
            }
            return fqn;
        }

        var splitName = new ArrayList<>(Arrays.asList(jvm.split("/")));
        var name = splitName.removeLast();
        var packageName = String.join(".", splitName);
        var byInner = name.split("\\$");

        fqn = packageName;
        if (!fqn.isBlank()) fqn += ".";
        fqn += String.join(".", byInner);
        jvmToFqn.put(jvm, fqn);

        // Skip creating a stub if the class is visible to JST already
        if (facade.findClass(fqn, GlobalSearchScope.everythingScope(facade.getProject())) != null) {
            return fqn;
        }

        StubClass stub = stubs.computeIfAbsent(packageName, $ -> new HashMap<>()).computeIfAbsent(byInner[0], $ -> new StubClass(byInner[0]));
        for (int i = 1; i < byInner.length; i++) {
            stub = stub.getChildren(byInner[i]);
        }
        addToStub(member, isMethod, stub);
        stubsByFqn.put(fqn, stub);

        return fqn;
    }

    private static void addToStub(String member, boolean isMethod, StubClass stub) {
        if (isMethod) {
            stub.methodNames().add(member);
        } else {
            stub.fieldNames().add(member);
        }
    }

    public synchronized void save(Path path) throws IOException {
        if (path.getParent() != null && !Files.isDirectory(path.getParent())) {
            Files.createDirectories(path.getParent());
        }

        try (var zos = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(path)))) {
            for (var entry : this.stubs.entrySet()) {
                var pkg = entry.getKey();
                var stubs = entry.getValue();
                String baseDeclaration = pkg.isBlank() ? "" : ("package " + pkg + ";\n\n");
                String baseFileName = pkg.isBlank() ? "" : (pkg.replace('.', '/') + "/");
                for (StubClass stub : stubs.values()) {
                    var builder = new StringBuilder(baseDeclaration);
                    builder.append("import net.neoforged.fml.common.asm.enumextension.EnumProxy;\n\n");
                    stub.save(s -> builder.append(s).append('\n'));

                    zos.putNextEntry(new ZipEntry(baseFileName + stub.name() + ".java"));
                    zos.write(builder.toString().getBytes(StandardCharsets.UTF_8));
                    zos.closeEntry();
                }
            }
        }
    }
    
    public record StubClass(String name, Set<String> fieldNames, Set<String> methodNames, Map<String, StubClass> children) {
        public StubClass(String name) {
            this(name, new HashSet<>(), new HashSet<>(), new HashMap<>());
        }

        public StubClass getChildren(String name) {
            return children.computeIfAbsent(name, StubClass::new);
        }
        
        public void save(Consumer<String> consumer) {
            consumer.accept("public class " + name + " {");
            fieldNames.stream().sorted()
                    .forEach(field -> consumer.accept("    public static final EnumProxy " + field + " = null;"));
            methodNames.stream().sorted()
                    .forEach(method -> consumer.accept("    public static Object " + method + "(int idx, Class type) { return null; }"));
            for (StubClass child : children.values()) {
                child.save(str -> consumer.accept("    " + str));
            }
            consumer.accept("}");
        }
    }
}
