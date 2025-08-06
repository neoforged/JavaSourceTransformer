package net.neoforged.jst.tests;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.intellij.util.ArrayUtil;
import net.neoforged.jst.cli.Main;
import net.neoforged.problems.FileProblemReporter;
import net.neoforged.problems.Problem;
import org.assertj.core.util.CanIgnoreReturnValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Test that references to external classes in method signatures are correctly resolved.
 */
public class EmbeddedTest {
    private final Path testDataRoot = Paths.get(getRequiredSystemProperty("jst.testDataDir"));

    @TempDir
    private Path tempDir;

    @Nested
    class SingleFileSource {

        @Test
        void singleFileOutput() throws Exception {
            var singleFile = testDataRoot.resolve("single_file/Test.java");
            var outputFile = tempDir.resolve("Test.java");

            runTool(singleFile.toString(), outputFile.toString());

            assertThat(loadDirToMap(tempDir)).isEqualTo(loadDirToMap(singleFile.getParent()));
        }

        @Test
        void singleFileOutputThatAlreadyExists() throws Exception {
            var singleFile = testDataRoot.resolve("single_file/Test.java");
            var outputFile = tempDir.resolve("Test.java");
            Files.write(outputFile, new byte[0]);

            runTool(singleFile.toString(), outputFile.toString());

            assertThat(loadDirToMap(tempDir)).isEqualTo(loadDirToMap(singleFile.getParent()));
        }

        @Test
        void singleFileOutputThatIsAFolder() throws Exception {
            var singleFile = testDataRoot.resolve("single_file/Test.java");
            // If the target exists and is a folder, it should create Test.java in it
            var outputFile = tempDir;

            runTool(singleFile.toString(), outputFile.toString());

            assertThat(loadDirToMap(tempDir)).isEqualTo(loadDirToMap(singleFile.getParent()));
        }

        @Test
        void folderOutput() throws Exception {
            var singleFile = testDataRoot.resolve("single_file/Test.java");
            var outputFile = tempDir;

            runTool(singleFile.toString(), "--out-format", "folder", outputFile.toString());

            var actualContent = loadDirToMap(tempDir);
            var expectedContent = loadDirToMap(singleFile.getParent());
            assertThat(actualContent).isEqualTo(expectedContent);
        }

        @Test
        void archiveOutput() throws Exception {
            var singleFile = testDataRoot.resolve("single_file/Test.java");
            var outputFile = tempDir.resolve("archive.zip");

            runTool(singleFile.toString(), "--out-format", "archive", outputFile.toString());

            var actualContent = loadZipToMap(outputFile);
            var expectedCotent = truncateTimes(loadDirToMap(singleFile.getParent()));
            assertThat(actualContent).isEqualTo(expectedCotent);
        }
    }

    @Nested
    class FolderSource {
        @Test
        void singleFileOutput() {
            var sourceFolder = testDataRoot.resolve("parchment/nested/source");
            var outputFile = tempDir.resolve("Output.java");

            // We do not have a unified exception here
            var e = assertThrows(Throwable.class, () -> {
                runTool(sourceFolder.toString(), "--out-format", "file", outputFile.toString());
            });
            assertThat(e).hasMessageContaining("Cannot have an input with possibly more than one file when the output is a single file.");
        }

        @Test
        void folderOutput() throws Exception {
            var sourceFolder = testDataRoot.resolve("parchment/nested/source");

            runTool(sourceFolder.toString(), tempDir.toString());

            assertThat(loadDirToMap(tempDir)).isEqualTo(loadDirToMap(sourceFolder));
        }

        @Test
        void archiveOutput() throws Exception {
            var sourceFolder = testDataRoot.resolve("parchment/nested/source");
            var outputFile = tempDir.resolve("archive.zip");

            runTool(sourceFolder.toString(), "--out-format", "archive", outputFile.toString());

            var actualContent = loadZipToMap(outputFile);
            var expectedContent = truncateTimes(loadDirToMap(sourceFolder));
            assertThat(actualContent).isEqualTo(expectedContent);
        }
    }

    @Nested
    class ArchiveSource {
        Path inputFile;
        Map<String, DirectoryTreeElement> expectedContent;

        @BeforeEach
        void setUp() throws IOException {
            inputFile = tempDir.resolve("input.zip");
            var sourceFolder = testDataRoot.resolve("parchment/nested/source");
            zipDirectory(sourceFolder, inputFile, p -> true);
            expectedContent = truncateTimes(loadDirToMap(sourceFolder));
        }

        @Test
        void singleFileOutput() {
            var outputFile = tempDir.resolve("Test.java");

            // We do not have a unified exception here
            var e = assertThrows(Throwable.class, () -> {
                runTool(inputFile.toString(), "--out-format", "file", outputFile.toString());
            });
            assertThat(e).hasMessageContaining("Cannot have an input with possibly more than one file when the output is a single file.");
        }

        @Test
        void folderOutput() throws Exception {
            var outputFolder = tempDir.resolve("output");
            Files.createDirectories(outputFolder);

            runTool(inputFile.toString(), outputFolder.toString());

            assertThat(loadDirToMap(outputFolder)).isEqualTo(expectedContent);
        }

        @Test
        void archiveOutput() throws Exception {
            var sourceFolder = testDataRoot.resolve("parchment/nested/source");
            var outputFile = tempDir.resolve("archive.zip");

            runTool(sourceFolder.toString(), "--out-format", "archive", outputFile.toString());

            var actualContent = loadZipToMap(outputFile);
            assertThat(actualContent).isEqualTo(expectedContent);
        }
    }

    @Nested
    class Parchment {
        @Test
        void testInnerAndLocalClasses() throws Exception {
            runParchmentTest("nested", "parchment.json");
        }

        @Test
        void testExternalReferences() throws Exception {
            runParchmentTest("external_refs", "parchment.json");
        }

        @Test
        void testPartialMatches() throws Exception {
            runParchmentTest("partial_matches", "parchment.json");
        }

        @Test
        void testParamIndices() throws Exception {
            runParchmentTest("param_indices", "parchment.json");
        }

        @Test
        void testJavadoc() throws Exception {
            runParchmentTest("javadoc", "parchment.json");
        }

        @Test
        void testTsrgMappings() throws Exception {
            runParchmentTest("tsrg_file", "merged.tsrg");
        }

        @Test
        void testAnonymousClasses() throws Exception {
            runParchmentTest("anonymous_classes", "mappings.tsrg");
        }

        @Test
        void testConflicts() throws Exception {
            runParchmentTest("conflicts", "mappings.tsrg", "--parchment-conflict-prefix=p_");
        }
    }

    @Nested
    class AccessTransformer {
        @Test
        void testFields() throws Exception {
            runATTest("fields");
        }

        @Test
        void testMethods() throws Exception {
            runATTest("methods");
        }

        @Test
        void testClasses() throws Exception {
            runATTest("classes");
        }

        @Test
        void testInnerClasses() throws Exception {
            runATTest("inner_classes");
        }

        @Test
        void testInnerMembers() throws Exception {
            runATTest("inner_members");
        }

        @Test
        void testWildcard() throws Exception {
            runATTest("wildcard");
        }

        @Test
        void testInterfaces() throws Exception {
            runATTest("interfaces");
        }

        @Test
        void testNoModifiers() throws Exception {
            runATTest("no_modifiers");
        }

        @Test
        void testWildcardAndExplicit() throws Exception {
            runATTest("wildcard_and_explicit");
        }

        @Test
        void testMissingTarget() throws Exception {
            runATTest("missing_target");
        }

        @Test
        void testImplicitConstructors() throws Exception {
            runATTest("implicit_constructors");
        }

        @Test
        void testIllegal() throws Exception {
            runATTest("illegal");
        }

        @Test
        void testFolderClasspathEntries() throws Exception {
            runATTest("folder_classpath_entry", "--classpath=" + testDataRoot.resolve("accesstransformer/folder_classpath_entry/deps"));
        }
    }

    @Nested
    class InterfaceInjection {
        @TempDir
        Path tempDir;

        @Test
        void testSimpleInjection() throws Exception {
            runInterfaceInjectionTest("simple_injection", tempDir);
        }

        @Test
        void testAdditiveInjection() throws Exception {
            runInterfaceInjectionTest("additive_injection", tempDir);
        }

        @Test
        void testInterfaceTarget() throws Exception {
            runInterfaceInjectionTest("interface_target", tempDir);
        }

        @Test
        void testStubs() throws Exception {
            runInterfaceInjectionTest("stubs", tempDir);
        }

        @Test
        void testInnerStubs() throws Exception {
            runInterfaceInjectionTest("inner_stubs", tempDir);
        }

        @Test
        void testMultipleInterfaces() throws Exception {
            runInterfaceInjectionTest("multiple_interfaces", tempDir);
        }

        @Test
        void testInjectedMarker() throws Exception {
            runInterfaceInjectionTest("injected_marker", tempDir, "--interface-injection-marker", "com/markers/InjectedMarker");
        }

        @Test
        void testGenerics() throws Exception {
            runInterfaceInjectionTest("generics", tempDir);
        }

        @Test
        void testNestedGenericStubs() throws Exception {
            runInterfaceInjectionTest("nested_generic_stubs", tempDir);
        }
    }

    @Nested
    class Unpick {
        @Test
        void testConst() throws Exception {
            runUnpickTest("const");
        }

        @Test
        void testFormats() throws Exception {
            runUnpickTest("formats");
        }

        @Test
        void testScoped() throws Exception {
            runUnpickTest("scoped");
        }

        @Test
        void testLocalVariables() throws Exception {
            runUnpickTest("local_variables");
        }

        @Test
        void testReturns() throws Exception {
            runUnpickTest("returns");
        }

        @Test
        void testFlags() throws Exception {
            runUnpickTest("flags");
        }

        @Test
        void testStatements() throws Exception {
            runUnpickTest("statements");
        }
    }

    protected final void runInterfaceInjectionTest(String testDirName, Path tempDir, String... additionalArgs) throws Exception {
        var stub = tempDir.resolve("jst-" + testDirName + "-stub.jar");
        testDirName = "interfaceinjection/" + testDirName;
        var testDir = testDataRoot.resolve(testDirName);
        var inputPath = testDir.resolve("injectedinterfaces.json");

        var args = new ArrayList<>(Arrays.asList("--enable-interface-injection", "--interface-injection-stubs", stub.toAbsolutePath().toString(), "--interface-injection-data", inputPath.toString()));
        args.addAll(Arrays.asList(additionalArgs));

        runTest(testDirName, UnaryOperator.identity(), args.toArray(String[]::new));

        if (Files.exists(testDir.resolve("expected_stub"))) {
            assertZipEqualsDir(stub, testDir.resolve("expected_stub"));
        }
    }

    protected final void runUnpickTest(String testDirName, String... additionalArgs) throws Exception {
        testDirName = "unpick/" + testDirName;
        var testDir = testDataRoot.resolve(testDirName);
        var inputPath = testDir.resolve("def.unpick");

        var args = new ArrayList<>(Arrays.asList("--enable-unpick", "--unpick-data", inputPath.toString()));
        args.addAll(Arrays.asList(additionalArgs));

        runTest(testDirName, UnaryOperator.identity(), args.toArray(String[]::new));
    }

    protected final void runATTest(String testDirName, final String... extraArgs) throws Exception {
        testDirName = "accesstransformer/" + testDirName;
        var atPath = testDataRoot.resolve(testDirName).resolve("accesstransformer.cfg");
        runTest(testDirName, txt -> txt.replace(atPath.toAbsolutePath().toString(), "{atpath}"), ArrayUtil.mergeArrays(
                new String[]{
                        "--enable-accesstransformers", "--access-transformer", atPath.toString()
                },
                extraArgs
        ));
    }

    protected final void runParchmentTest(String testDirName, String mappingsFilename, String... extraArgs) throws Exception {
        testDirName = "parchment/" + testDirName;
        var args = new ArrayList<>(Arrays.asList("--enable-parchment", "--parchment-mappings", testDataRoot.resolve(testDirName).resolve(mappingsFilename).toString()));
        args.addAll(Arrays.asList(extraArgs));
        runTest(testDirName, UnaryOperator.identity(), args.toArray(String[]::new));
    }

    protected final void runTest(String testDirName, UnaryOperator<String> consoleMapper, String... args) throws Exception {
        var testDir = testDataRoot.resolve(testDirName);
        var sourceDir = testDir.resolve("source");
        var expectedDir = testDir.resolve("expected");

        var inputFile = tempDir.resolve("input.jar");
        zipDirectory(sourceDir, inputFile, path ->
                Files.isDirectory(path) || path.getFileName().toString().endsWith(".java"));

        var outputFile = tempDir.resolve("output.jar");

        // For testing external references, add JUnit-API, so it can be referenced
        var junitJarPath = Paths.get(Test.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        var librariesFile = tempDir.resolve("libraries.txt");
        Files.write(librariesFile, List.of("-e=" + junitJarPath));

        var reportFile = tempDir.resolve("report.json");
        var expectedReport = testDir.resolve("expected_report.json");

        final List<String> arguments = new ArrayList<>(Arrays.asList(
                "--max-queue-depth=1",
                "--libraries-list",
                librariesFile.toString()));

        if (Files.exists(expectedReport)) {
            arguments.add("--problems-report");
            arguments.add(reportFile.toString());
        }

        arguments.addAll(Arrays.asList(args));
        arguments.add(inputFile.toString());
        arguments.add(outputFile.toString());
        var consoleOut = consoleMapper.apply(runTool(arguments.toArray(String[]::new)));

        assertZipEqualsDir(outputFile, expectedDir);

        var expectedLog = testDir.resolve("expected.log");
        if (Files.exists(expectedLog)) {
            var expectedLogContent = Files.readString(expectedLog);
            assertThat(consoleOut).isEqualToNormalizingNewlines(expectedLogContent);
        }

        if (Files.exists(expectedReport)) {
            var expectedRecords = FileProblemReporter.loadRecords(expectedReport);
            var actualRecords = FileProblemReporter.loadRecords(reportFile);

            // Relativize the paths to make them comparable to the reference data.
            actualRecords = actualRecords.stream().map(record -> {
                        if (record.location() == null) {
                            return record;
                        }
                        return Problem.builder(record)
                                .location(record.location().withFile(testDir.relativize(record.location().file())))
                                .build();
                    }
            ).toList();

            assertEquals(problemsToJson(expectedRecords), problemsToJson(actualRecords));
        }
    }

    private String problemsToJson(List<Problem> problems) {
        return new GsonBuilder()
                .setPrettyPrinting()
                .registerTypeHierarchyAdapter(Path.class, new TypeAdapter<Path>() {
                    public void write(JsonWriter out, Path value) throws IOException {
                        out.value(value.toString().replace('\\', '/'));
                    }

                    public Path read(JsonReader in) throws IOException {
                        return Paths.get(in.nextString());
                    }
                })
                .create().toJson(problems);
    }

    protected final void assertZipEqualsDir(Path zip, Path expectedDir) throws IOException {
        try (var zipFile = new ZipFile(zip.toFile())) {
            var it = zipFile.entries().asIterator();
            while (it.hasNext()) {
                var entry = it.next();
                if (entry.isDirectory()) {
                    continue;
                }

                var actualFile = normalizeLines(new String(zipFile.getInputStream(entry).readAllBytes(), StandardCharsets.UTF_8));

                var path = expectedDir.resolve(entry.getName());
                if (Files.exists(path)) {
                    var expectedFile = normalizeLines(Files.readString(path, StandardCharsets.UTF_8));
                    assertEquals(expectedFile, actualFile);
                } else {
                    assertThat("<file doesn't exist>")
                            .describedAs("Expected content at " + path + " but file wasn't found")
                            .isEqualTo(actualFile);
                }
            }
        }
    }

    @CanIgnoreReturnValue
    protected String runTool(String... args) throws Exception {
        // This is thread hostile, but what can I do :-[
        var oldOut = System.out;
        var oldErr = System.err;
        var capturedOut = new ByteArrayOutputStream();
        int exitCode;
        try {
            System.setErr(new PrintStream(capturedOut));
            System.setOut(new PrintStream(capturedOut));
            // Run in-process for easier debugging
            exitCode = Main.innerMain(args);
        } finally {
            System.setErr(oldErr);
            System.setOut(oldOut);
        }

        var capturedOutString = capturedOut.toString(StandardCharsets.UTF_8);

        if (exitCode != 0) {
            throw new RuntimeException("Process failed with exit code 0: " + capturedOutString);
        }

        return capturedOutString;
    }

    protected static String getRequiredSystemProperty(String key) {
        var value = System.getProperty(key);
        if (value == null) {
            throw new RuntimeException("Missing system property: " + key);
        }
        return value;
    }

    private String normalizeLines(String s) {
        return s.replaceAll("\r\n", "\n");
    }

    private static void zipDirectory(Path directory, Path destinationPath, Predicate<Path> filter) throws IOException {
        try (var zOut = new ZipOutputStream(Files.newOutputStream(destinationPath));
             var files = Files.walk(directory)) {
            files.filter(filter).forEach(path -> {
                // Skip visiting the root directory itself
                if (path.equals(directory)) {
                    return;
                }

                var relativePath = directory.relativize(path).toString().replace('\\', '/');

                try {
                    if (Files.isDirectory(path)) {
                        var entry = new ZipEntry(relativePath + "/");
                        zOut.putNextEntry(entry);
                        zOut.closeEntry();
                    } else {
                        var entry = new ZipEntry(relativePath);
                        entry.setLastModifiedTime(Files.getLastModifiedTime(path));

                        zOut.putNextEntry(entry);
                        Files.copy(path, zOut);
                        zOut.closeEntry();
                    }
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }
    }

    private static Map<String, DirectoryTreeElement> loadZipToMap(Path archive) throws IOException {
        var result = new HashMap<String, DirectoryTreeElement>();

        try (var zin = new ZipInputStream(Files.newInputStream(archive))) {
            for (var entry = zin.getNextEntry(); entry != null; entry = zin.getNextEntry()) {
                if (entry.isDirectory()) {
                    // strip trailing /
                    var name = entry.getName();
                    if (name.endsWith("/")) {
                        name = name.substring(0, name.length() - 1);
                    }
                    result.put(name, new Directory());
                } else if (entry.getName().endsWith(".java")) {
                    result.put(entry.getName(), new TextFile(
                            entry.getLastModifiedTime(),
                            new String(zin.readAllBytes(), StandardCharsets.UTF_8)
                    ));
                } else {
                    result.put(entry.getName(), new BinaryFile(
                            entry.getLastModifiedTime(),
                            zin.readAllBytes()
                    ));
                }
            }
        }

        return result;
    }

    /**
     * Truncates times to the same precision as used by ZIP-files
     */
    private static Map<String, DirectoryTreeElement> truncateTimes(Map<String, DirectoryTreeElement> tree) throws IOException {
        return tree.entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey,
                v -> {
                    if (v.getValue() instanceof BinaryFile binaryFile) {
                        return new BinaryFile(truncateTime(binaryFile.fileTime), binaryFile.content());
                    } else if (v.getValue() instanceof TextFile textFile) {
                        return new TextFile(truncateTime(textFile.fileTime), textFile.content());
                    } else {
                        return v.getValue();
                    }
                }
        ));
    }

    private static FileTime truncateTime(FileTime fileTime) {
        // Apparently it can only do millisecond precision, weird.
        return FileTime.from(fileTime.toMillis() / 1000, TimeUnit.SECONDS);
    }

    private static Map<String, DirectoryTreeElement> loadDirToMap(Path folder) throws IOException {
        try (Stream<Path> stream = Files.walk(folder)) {
            return stream
                    .filter(p -> !p.equals(folder))
                    .collect(Collectors.toMap(
                            p -> folder.relativize(p).toString().replace('\\', '/'),
                            p -> {
                                try {
                                    if (Files.isDirectory(p)) {
                                        return new Directory();
                                    } else if (p.getFileName().toString().endsWith(".java")) {
                                        return new TextFile(
                                                Files.getLastModifiedTime(p),
                                                Files.readString(p, StandardCharsets.UTF_8)
                                        );
                                    } else {
                                        return new BinaryFile(
                                                Files.getLastModifiedTime(p),
                                                Files.readAllBytes(p)
                                        );
                                    }
                                } catch (IOException e) {
                                    throw new UncheckedIOException(e);
                                }
                            }
                    ));
        }
    }

    interface DirectoryTreeElement {
    }

    record Directory() implements DirectoryTreeElement {
    }

    record TextFile(FileTime fileTime, String content) implements DirectoryTreeElement {
    }

    record BinaryFile(FileTime fileTime, byte[] content) implements DirectoryTreeElement {
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            BinaryFile that = (BinaryFile) o;
            return Objects.equals(fileTime, that.fileTime) && Arrays.equals(content, that.content);
        }

        @Override
        public int hashCode() {
            int result = Objects.hash(fileTime);
            result = 31 * result + Arrays.hashCode(content);
            return result;
        }
    }
}
