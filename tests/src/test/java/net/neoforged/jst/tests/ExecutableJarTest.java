package net.neoforged.jst.tests;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Runs the same tests as {@link EmbeddedTest}, but runs them by actually running the executable jar
 * in an external process.
 */
public class ExecutableJarTest extends EmbeddedTest {
    @Override
    protected void runTool(String... args) throws Exception {
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
}
