package net.neoforged.jst.cli;

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
    @CommandLine.Parameters(index = "0", paramLabel = "INPUT", description = "Path to a single Java-file, a source-archive or a folder containing the source to transform.")
    private Path inputPath;

    @CommandLine.Parameters(index = "1", paramLabel = "OUTPUT", description = "Path to where the resulting source should be placed.")
    private Path outputPath;

    @CommandLine.Option(names = "--in-format", description = "Specify the format of INPUT explicitly. AUTO (the default) performs auto-detection. Other options are SINGLE_FILE for Java files, ARCHIVE for source jars or zips, and FOLDER for folders containing Java code.")
    private PathType inputFormat = PathType.AUTO;

    @CommandLine.Option(names = "--out-format", description = "Specify the format of OUTPUT explicitly. Allows the same options as --in-format.")
    private PathType outputFormat = PathType.AUTO;

    @CommandLine.Option(names = "--libraries-list", description = "Specifies a file that contains a path to an archive or directory to add to the classpath on each line.")
    private Path librariesList;

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
        var spec = commandLine.getCommandSpec();

        main.setupPluginCliOptions(plugins, spec);
        return commandLine.execute(args);
    }

    @Override
    public Integer call() throws Exception {

        try (var source = FileSources.create(inputPath, inputFormat);
             var processor = new SourceFileProcessor()) {

            if (librariesList != null) {
                processor.addLibrariesList(librariesList);
            }

            var orderedTransformers = new ArrayList<>(enabledTransformers);

            try (var sink = FileSinks.create(outputPath, outputFormat, source)) {
                processor.process(source, sink, orderedTransformers);
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

//
//    void poo() {
//        String[] args = new String[0];
//
//        Path inputPath = null, outputPath = null, namesAndDocsPath = null, librariesPath = null;
//        boolean enableJavadoc = true;
//        int queueDepth = 50;
//
//        for (int i = 0; i < args.length; i++) {
//            var arg = args[i];
//            switch (arg) {
//                case "--in":
//                    if (i + 1 >= args.length) {
//                        System.err.println("Missing argument for --in");
//                        System.exit(1);
//                    }
//                    inputPath = Paths.get(args[++i]);
//                    break;
//                case "--out":
//                    if (i + 1 >= args.length) {
//                        System.err.println("Missing argument for --out");
//                        System.exit(1);
//                    }
//                    outputPath = Paths.get(args[++i]);
//                    break;
//                case "--libraries":
//                    if (i + 1 >= args.length) {
//                        System.err.println("Missing argument for --libraries");
//                        System.exit(1);
//                    }
//                    librariesPath = Paths.get(args[++i]);
//                    break;
//                case "--names":
//                    if (i + 1 >= args.length) {
//                        System.err.println("Missing argument for --names");
//                        System.exit(1);
//                    }
//                    namesAndDocsPath = Paths.get(args[++i]);
//                    break;
//                case "--skip-javadoc":
//                    enableJavadoc = false;
//                    break;
//                case "--queue-depth":
//                    if (i + 1 >= args.length) {
//                        System.err.println("Missing argument for --queue-depth");
//                        System.exit(1);
//                    }
//                    queueDepth = Integer.parseUnsignedInt(args[++i]);
//                    break;
//                case "--help":
//                    printUsage(System.out);
//                    System.exit(0);
//                    break;
//                default:
//                    System.err.println("Unknown argument: " + arg);
//                    printUsage(System.err);
//                    System.exit(1);
//                    break;
//            }
//        }
//
//        if (inputPath == null || outputPath == null || namesAndDocsPath == null) {
//            System.err.println("Missing arguments");
//            printUsage(System.err);
//            System.exit(1);
//        }
//
//    }
}
