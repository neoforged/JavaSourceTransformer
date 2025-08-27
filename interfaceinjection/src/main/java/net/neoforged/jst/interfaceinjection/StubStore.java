package net.neoforged.jst.interfaceinjection;

import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.search.GlobalSearchScope;
import net.neoforged.jst.api.Logger;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * When injecting interfaces, we generate basic interface stubs that can be used as a separate
 * artifact when recompiling the transformed code, to avoid putting the actual interfaces on the classpath
 * and risking circular dependencies.
 */
class StubStore {
    private final Logger logger;
    private final JavaPsiFacade facade;
    private final Map<String, String> jvmToFqn = new HashMap<>();
    private final Map<String, Map<String, StubInterface>> stubs = new HashMap<>();

    StubStore(Logger logger, JavaPsiFacade facade) {
        this.logger = logger;
        this.facade = facade;
    }

    public InterfaceInformation createStub(String jvm) {
        String generics = "";
        int typeParameterCount = 0;

        var genericsStart = jvm.indexOf('<');
        if (genericsStart != -1) {
            var genericsEnd = jvm.lastIndexOf('>');
            if (genericsEnd == -1 || genericsEnd < genericsStart) {
                logger.error("Interface injection %s has incomplete generics declarations", jvm);
            } else {
                generics = jvm.substring(genericsStart + 1, genericsEnd);
                if (generics.isBlank()) {
                    logger.error("Interface injection %s has blank type parameters", jvm);
                } else {
                    var reader = new StringReader(generics);
                    List<String> typeArgs = new ArrayList<>();
                    while (reader.hasNext()) {
                        typeArgs.add(stubGenericArguments(reader));
                        reader.skipWhitespace();
                        if (reader.hasNext() && reader.next() != ',') {
                            logger.error("Interface injection generics declaration %s is invalid", generics);
                        }
                    }

                    generics = String.join(", ", typeArgs);
                    typeParameterCount = typeArgs.size();
                }
            }
            jvm = jvm.substring(0, genericsStart);
        }

        return new InterfaceInformation(createStub(jvm, typeParameterCount), generics);
    }

    private static final Pattern BOUNDED_WILDCARD_PATTERN = Pattern.compile("\\?\\s+(extends|super)\\s+(.+)");

    private String stubGenericArguments(StringReader generics) {
        StringBuilder typeName = new StringBuilder();
        List<String> genericArgs = new ArrayList<>();
        while (generics.hasNext() && generics.peek() != ',' && generics.peek() != '>') {
            var ch = generics.next();
            if (ch == '<') {
                do {
                    genericArgs.add(stubGenericArguments(generics));
                    generics.skipWhitespace();
                } while (generics.next() != '>'); // The next character can either be a comma or a >. If it's a > we exit the generic declaration, otherwise we consume the comma and stub the next argument
                break; // No point in continuing to parse if we found and parsed the nested generic arguments
            } else {
                typeName.append(ch);
            }
        }

        String base;

        var type = typeName.toString().trim();
        // Within bounded wildcards (? extends X) or (? super X) we need to make sure we stub the type
        var boundedMatcher = BOUNDED_WILDCARD_PATTERN.matcher(type);
        if (boundedMatcher.matches()) {
            var name = boundedMatcher.group(2);
            base = "? " + boundedMatcher.group(1) + " " + possiblyStubTypeName(name, genericArgs.size());
        } else {
            base = possiblyStubTypeName(type, genericArgs.size());
        }

        if (genericArgs.isEmpty()) {
            return base;
        } else {
            return base + "<" + String.join(", ", genericArgs) + ">";
        }
    }

    private String possiblyStubTypeName(String name, int genericCount) {
        // If the type argument contains a dot we assume it is a class, so we have to stub it
        if (name.contains(".")) {
            return createStub(name.replace('.', '/'), genericCount);
        } else {
            // Otherwise, it could be a wildcard or it could be another type parameter
            return name;
        }
    }

    private synchronized String createStub(String jvm, int typeParameterCount) {
        var fqn = jvmToFqn.get(jvm);
        if (fqn != null) return fqn;

        var splitName = new ArrayList<>(Arrays.asList(jvm.split("/")));
        var name = splitName.remove(splitName.size() - 1);
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

        StubInterface stub = stubs.computeIfAbsent(packageName, $ -> new HashMap<>()).computeIfAbsent(byInner[0], $ -> new StubInterface(byInner[0]));
        for (int i = 1; i < byInner.length; i++) {
            stub = stub.getChildren(byInner[i]);
        }
        stub.typeParameterCount().set(typeParameterCount);

        return fqn;
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

    public record StubInterface(String name, AtomicInteger typeParameterCount, Map<String, StubInterface> children) {
        public StubInterface(String name) {
            this(name, new AtomicInteger(), new HashMap<>());
        }

        public StubInterface getChildren(String name) {
            return children.computeIfAbsent(name, StubInterface::new);
        }

        public void save(Consumer<String> consumer) {
            var generics = "";
            if (typeParameterCount.get() > 0) {
                generics = "<" + IntStream.range(0, typeParameterCount.get())
                        .mapToObj(i -> Character.toString((char)('A' + i)))
                        .collect(Collectors.joining(", ")) + ">";
            }

            consumer.accept("public interface " + name + generics + " {");
            for (StubInterface child : children.values()) {
                child.save(str -> consumer.accept("    " + str));
            }
            consumer.accept("}");
        }
    }

    record InterfaceInformation(String interfaceDeclaration, String generics) {
        @Override
        public String toString() {
            return generics.isBlank() ? interfaceDeclaration : interfaceDeclaration + "<" + generics + ">";
        }
    }

    private static class StringReader {
        private final String string;
        private int i = -1;

        private StringReader(String string) {
            this.string = string;
        }

        public boolean hasNext() {
            return i < string.length() - 1;
        }

        public char peek() {
            return string.charAt(i + 1);
        }

        public char next() {
            return string.charAt(++i);
        }

        public void skipWhitespace() {
            while (hasNext() && peek() == ' ') next();
        }

        @Override
        public String toString() {
            return string;
        }
    }
}
