package net.neoforged.jst.tests;

import net.neoforged.jst.cli.Main;
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
    }

    protected final void runATTest(String testDirName) throws Exception {
        testDirName = "accesstransformer/" + testDirName;
        runTest(testDirName, "--enable-accesstransformers", "--access-transformer", testDataRoot.resolve(testDirName).resolve("accesstransformer.cfg").toString());
    }

    protected final void runParchmentTest(String testDirName, String mappingsFilename) throws Exception {
        testDirName = "parchment/" + testDirName;
        runTest(testDirName, "--enable-parchment", "--parchment-mappings", testDataRoot.resolve(testDirName).resolve(mappingsFilename).toString());
    }

    protected final void runTest(String testDirName, String... args) throws Exception {
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

        final List<String> arguments = new ArrayList<>(Arrays.asList(
                "--max-queue-depth=1",
                "--libraries-list",
                librariesFile.toString()));
        arguments.addAll(Arrays.asList(args));
        arguments.add(inputFile.toString());
        arguments.add(outputFile.toString());
        runTool(arguments.toArray(String[]::new));

        try (var zipFile = new ZipFile(outputFile.toFile())) {
            var it = zipFile.entries().asIterator();
            while (it.hasNext()) {
                var entry = it.next();
                if (entry.isDirectory()) {
                    continue;
                }

                var actualFile = normalizeLines(new String(zipFile.getInputStream(entry).readAllBytes(), StandardCharsets.UTF_8));
                var expectedFile = normalizeLines(Files.readString(expectedDir.resolve(entry.getName()), StandardCharsets.UTF_8));
                assertEquals(expectedFile, actualFile);
            }
        }
    }

    protected void runTool(String... args) throws Exception {
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
