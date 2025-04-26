package net.neoforged.jst.cli;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import net.neoforged.jst.api.Logger;
import net.neoforged.jst.api.ProblemId;
import net.neoforged.jst.api.ProblemLocation;
import net.neoforged.jst.api.ProblemReporter;
import net.neoforged.jst.api.ProblemSeverity;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.VisibleForTesting;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@ApiStatus.Internal
public class FileProblemReporter implements ProblemReporter, AutoCloseable {
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeHierarchyAdapter(Path.class, new TypeAdapter<Path>() {
                @Override
                public void write(JsonWriter out, Path value) throws IOException {
                    out.value(value.toAbsolutePath().toString());
                }

                @Override
                public Path read(JsonReader in) throws IOException {
                    return Paths.get(in.nextString());
                }
            })
            .create();

    private final Logger logger;
    private final Path problemsReport;

    private final List<ProblemRecord> problems = new ArrayList<>();

    public FileProblemReporter(Logger logger, Path problemsReport) {
        this.logger = logger;
        this.problemsReport = problemsReport;
    }

    @Override
    public void report(ProblemId problemId, ProblemSeverity severity, ProblemLocation location, String message) {
        problems.add(new ProblemRecord(problemId, severity, location, message));
    }

    @Override
    public void report(ProblemId problemId, ProblemSeverity severity, String message) {
        report(problemId, severity, null, message);
    }

    @Override
    public void close() throws IOException {
        logger.debug("Writing problems report to ", problemsReport);
        try (var writer = Files.newBufferedWriter(problemsReport, StandardCharsets.UTF_8)) {
            GSON.toJson(problems, writer);
        }
    }

    @VisibleForTesting
    public static List<ProblemRecord> loadRecords(Path file) throws IOException {
        try (var reader = Files.newBufferedReader(file)) {
            return Arrays.asList(GSON.fromJson(reader, ProblemRecord[].class));
        }
    }

    public record ProblemRecord(
            ProblemId problemId,
            ProblemSeverity severity,
            ProblemLocation location,
            String message
    ) {
    }
}
