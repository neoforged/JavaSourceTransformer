package net.neoforged.jst.parchment.namesanddocs.parchment;

import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ParchmentDatabaseTest {
    // A minimal Parchment JSON to validate that it loaded *something*
    @Language("JSON")
    private static final String EXAMPLE_JSON = """
            {
              "version": "1.1.0",
              "classes": [
                {
                  "name": "TestClass"
                }
              ]
            }
            """;

    @TempDir
    Path tempDir;

    @Test
    void testLoadFromJsonReader() {
        var db = ParchmentDatabase.loadJson(new StringReader(EXAMPLE_JSON));
        assertNotNull(db.getClass("TestClass"));
    }

    @Test
    void testLoadFromJsonFile() throws IOException {
        var tempFile = tempDir.resolve("test.json");
        Files.writeString(tempFile, EXAMPLE_JSON);

        var db = ParchmentDatabase.loadJson(tempFile);
        assertNotNull(db.getClass("TestClass"));
    }

    @Test
    void testLoadFromZipWithParchmentJson() throws IOException {
        var tempFile = tempDir.resolve("temp.zip");
        writeZip(tempFile, Map.of(
                "unrelated/parchment.json", "INVALID",
                "parchment.json", EXAMPLE_JSON
        ));

        var db = ParchmentDatabase.loadZip(tempFile);
        assertNotNull(db.getClass("TestClass"));
    }

    @Test
    void testLoadFromZipWithOtherJson() throws IOException {
        var tempFile = tempDir.resolve("temp.zip");
        writeZip(tempFile, Map.of(
                "unrelated/parchment.json", "INVALID",
                "other.json", EXAMPLE_JSON
        ));

        var db = ParchmentDatabase.loadZip(tempFile);
        assertNotNull(db.getClass("TestClass"));
    }

    @Test
    void testLoadFromZipWithMultipleOtherJson() throws IOException {
        var tempFile = tempDir.resolve("temp.zip");
        writeZip(tempFile, Map.of(
                "unrelated/parchment.json", "INVALID",
                "other.json", EXAMPLE_JSON,
                "yet_another_json.json", EXAMPLE_JSON
        ));

        assertThrows(FileNotFoundException.class, () -> ParchmentDatabase.loadZip(tempFile));
    }

    @Test
    void testLoadFromEmptyZip() throws IOException {
        var tempFile = tempDir.resolve("temp.zip");
        writeZip(tempFile, Map.of());

        assertThrows(FileNotFoundException.class, () -> ParchmentDatabase.loadZip(tempFile));
    }

    private static void writeZip(Path tempFile, Map<String, String> entries) throws IOException {
        try (var zf = new ZipOutputStream(Files.newOutputStream(tempFile))) {
            for (var entry : entries.entrySet()) {
                zf.putNextEntry(new ZipEntry(entry.getKey()));
                zf.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zf.closeEntry();
            }
        }
    }
}
