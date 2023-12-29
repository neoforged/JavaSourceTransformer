package net.neoforged.jst.tests;

import net.neoforged.jst.cli.Main;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Test that references to external classes in method signatures are correctly resolved.
 */
public class MainTest {
    @TempDir
    private Path tempDir;

    @Test
    void testInnerAndLocalClasses() throws Exception {
        runTest("nested");
    }

    @Test
    void testExternalReferences() throws Exception {
        runTest("external_refs");
    }

    @Test
    void testParamIndices() throws Exception {
        runTest("param_indices");
    }

    @Test
    void testJavadoc() throws Exception {
        runTest("javadoc");
    }

    protected final void runTest(String testDir) throws Exception {
        Path testDataRoot = Paths.get(getRequiredSystemProperty("jst.testDataDir"))
                .resolve(testDir);

        var parchmentFile = testDataRoot.resolve("parchment.json");
        var sourceDir = testDataRoot.resolve("source");
        var expectedDir = testDataRoot.resolve("expected");

        var inputFile = tempDir.resolve("input.jar");
        zipDirectory(sourceDir, inputFile, path -> {
            return Files.isDirectory(path) || path.getFileName().toString().endsWith(".java");
        });

        var outputFile = tempDir.resolve("output.jar");

        // For testing external references, add JUnit-API, so it can be referenced
        var junitJarPath = Paths.get(Test.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        var librariesFile = tempDir.resolve("libraries.txt");
        Files.write(librariesFile, List.of("-e=" + junitJarPath));

        runExecutableJar(
                "--libraries-list",
                librariesFile.toString(),
                "--enable-parchment",
                "--parchment-mappings",
                parchmentFile.toString(),
                inputFile.toString(),
                outputFile.toString()
        );

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

    private static void runExecutableJar(String... args) throws IOException, InterruptedException {
        // Run in-process for easier debugging
        if (Boolean.getBoolean("jst.debug")) {
            Main.innerMain(args);
            return;
        }

        var javaExecutablePath = ProcessHandle.current()
                .info()
                .command()
                .orElseThrow();

        List<String> commandLine = new ArrayList<>();
        commandLine.add(javaExecutablePath);
        commandLine.add("-jar");
        commandLine.add(getRequiredSystemProperty("jst.executableJar"));
        Collections.addAll(commandLine, args);

        var process = new ProcessBuilder(commandLine)
                .redirectErrorStream(true)
                .start();

        process.getOutputStream().close(); // Close stdin to java

        byte[] output = process.getInputStream().readAllBytes();
        System.out.println(new String(output));

        int exitCode = process.waitFor();
        assertEquals(0, exitCode);
    }

    private static String getRequiredSystemProperty(String key) {
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
}
