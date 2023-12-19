import com.intellij.util.io.ZipUtil;
import namesanddocs.NameAndDocSourceLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class Tests {
    @TempDir
    private Path tempDir;

    @Test
    void testNesting() throws Exception {

        var parchmentFile = Paths.get(getClass().getResource("/test1/parchment.json").toURI());
        var sourceDir = parchmentFile.resolveSibling("source");
        var expectedDir = parchmentFile.resolveSibling("expected");

        var inputFile = tempDir.resolve("input.jar");
        try (var zos = new ZipOutputStream(Files.newOutputStream(inputFile))) {
            ZipUtil.addDirToZipRecursively(zos, null, sourceDir.toFile(), "", file -> {
                return file.isDirectory() || file.getName().endsWith(".java");
            }, null);
        }

        var ouptutFile = tempDir.resolve("output.jar");
        try (var remapper = new ApplyParchmentToSourceJar(NameAndDocSourceLoader.load(parchmentFile))) {
            remapper.setMaxQueueDepth(0); // Easier to debug...
            remapper.apply(inputFile, ouptutFile);
        }

        try (var zipFile = new ZipFile(ouptutFile.toFile())) {
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

    private String normalizeLines(String s) {
        return s.replaceAll("\r\n", "\n");
    }
}
