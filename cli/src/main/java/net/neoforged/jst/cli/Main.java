package net.neoforged.jst.cli;

import net.neoforged.jst.api.Logger;
import net.neoforged.jst.api.SourceTransformer;
import net.neoforged.jst.api.SourceTransformerPlugin;
import net.neoforged.jst.cli.io.FileSinks;
import net.neoforged.jst.cli.io.FileSources;
import org.jetbrains.annotations.VisibleForTesting;
import picocli.CommandLine;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.Callable;

@CommandLine.Command(name = "jst", mixinStandardHelpOptions = true, usageHelpWidth = 100)
public class Main implements Callable<Integer> {
    @CommandLine.ArgGroup(exclusive = false, multiplicity = "1..*")
    List<Task> tasks;
    
    static class Task {
        @CommandLine.Parameters(index = "0", paramLabel = "INPUT", description = "Path to a single Java-file, a source-archive or a folder containing the source to transform.")
        Path inputPath;

        @CommandLine.Parameters(index = "1", paramLabel = "OUTPUT", description = "Path to where the resulting source should be placed.")
        Path outputPath;

        @CommandLine.Option(names = "--in-format", description = "Specify the format of INPUT explicitly. AUTO (the default) performs auto-detection. Other options are SINGLE_FILE for Java files, ARCHIVE for source jars or zips, and FOLDER for folders containing Java code.")
        PathType inputFormat = PathType.AUTO;

        @CommandLine.Option(names = "--out-format", description = "Specify the format of OUTPUT explicitly. Allows the same options as --in-format.")
        PathType outputFormat = PathType.AUTO;

        @CommandLine.Option(names = "--ignore-prefix", description = "Do not apply transformations to paths that start with any of these prefixes.")
        List<String> ignoredPrefixes = new ArrayList<>();
    }

    @CommandLine.Option(names = "--libraries-list", description = "Specifies a file that contains a path to an archive or directory to add to the classpath on each line.")
    Path librariesList;

    @CommandLine.Option(names = "--classpath", description = "Additional classpath entries to use. Is combined with --libraries-list.", converter = ClasspathConverter.class)
    List<Path> addToClasspath = new ArrayList<>();

    @CommandLine.Option(names = "--max-queue-depth", description = "When both input and output support ordering (archives), the transformer will try to maintain that order. To still process items in parallel, a queue is used. Larger queue depths lead to higher memory usage.")
    int maxQueueDepth = 100;

    @CommandLine.Command(name = "--debug", description = "Print additional debugging information")
    boolean debug = false;

    private final HashSet<SourceTransformer> enabledTransformers = new HashSet<>();

    public static void main(String[] args) {
        System.exit(innerMain(args));
    }

    @VisibleForTesting
    public static int innerMain(String... args) {
        // Load these up front so that they can add CommandLine Options
        var plugins = ServiceLoader.load(SourceTransformerPlugin.class).stream().map(ServiceLoader.Provider::get).toList();

        var main = new Main();
        var commandLine = new CommandLine(main);
        commandLine.setCaseInsensitiveEnumValuesAllowed(true);
        var spec = commandLine.getCommandSpec();

        main.setupPluginCliOptions(plugins, spec);
        return commandLine.execute(args);
    }

    @Override
    public Integer call() throws Exception {
        var logger = debug ? new Logger(System.out, System.err) : new Logger(null, System.err);
        for (var task : tasks) {
            try (var source = FileSources.create(task.inputPath, task.inputFormat);
                 var processor = new SourceFileProcessor(logger)) {

                if (librariesList != null) {
                    processor.addLibrariesList(librariesList);
                }
                for (Path path : addToClasspath) {
                    processor.addLibrary(path);
                }
                for (String ignoredPrefix : task.ignoredPrefixes) {
                    processor.addIgnoredPrefix(ignoredPrefix);
                }

                processor.setMaxQueueDepth(maxQueueDepth);

                var orderedTransformers = new ArrayList<>(enabledTransformers);

                try (var sink = FileSinks.create(task.outputPath, task.outputFormat, source)) {
                    if (!processor.process(source, sink, orderedTransformers)) {
                        logger.error("Transformation failed");
                        return 1;
                    }
                }
            }
        }

        return 0;
    }

    private void setupPluginCliOptions(List<SourceTransformerPlugin> plugins, CommandLine.Model.CommandSpec spec) {
        for (var plugin : plugins) {
            var transformer = plugin.createTransformer();

            var builder = CommandLine.Model.ArgGroupSpec.builder();
            builder
                    .exclusive(false)
                    .heading("Plugin - " + plugin.getName() + "%n");

            builder.addArg(CommandLine.Model.OptionSpec.builder("--enable-" + plugin.getName())
                    .type(boolean.class)
                    .required(true)
                    .setter(new CommandLine.Model.ISetter() {
                        @SuppressWarnings("unchecked")
                        @Override
                        public <T> T set(T value) {
                            var previous = enabledTransformers.contains(transformer);
                            if ((boolean) value) {
                                enabledTransformers.add(transformer);
                            } else {
                                enabledTransformers.remove(transformer);
                            }
                            return (T) (Object) previous;
                        }
                    })
                    .description("Enable " + plugin.getName())
                    .build());

            var transformerSpec = CommandLine.Model.CommandSpec.forAnnotatedObject(transformer);
            for (var option : transformerSpec.options()) {
                builder.addArg(option);
            }
            spec.addArgGroup(builder.build());
        }
    }
}
