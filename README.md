# Java Source Transformer

This project intends to be a command-line tool to apply configurable transformations to Java source-code without
user-interaction. It is intended to be integrated into the [NeoGradle](https://github.com/neoforged/NeoGradle) 
pipeline for producing a Minecraft Jar-File for use in modding projects.

The source code is parsed using [IntelliJ Community Edition](https://github.com/JetBrains/intellij-community) libraries.

## Transformations

### JavaDoc Injection (JavaDoctor)

Inserts additional class, method, and field Javadoc elements. The majority of these are sourced
from [Parchment](https://parchmentmc.org/).

### Method Parameter Names

During deobfuscation, the method parameter names are set to stable but automatically generated names.
To aid developers in understanding the decompiled code, and for better code generation when overriding decompiled
methods in mods, method parameter names are replaced with crowdsourced information from [Parchment](https://parchmentmc.org/).

### Interface Injection
JST supports injecting interfaces to transformed classes in a data-driven fashion, and creates **empty** stubs for the classes
to be able to still compile the modified code without access to the actual interface definitions.  
This feature allows interfaces added at runtime using Mixins, Coremods, or other transformation mechanics to be visible at compile-time.

The format of the interface injection data file is quite straightforward:
```json5
{
  "targetClassBinary": ["interfaceBinary"] // Can be a single interface, or an array of interfaces to implement
}
```
Where:
- `targetClassBinary` is the binary representation of the target class to inject the interfaces to (e.g. `net/minecraft/world/item/Item`, or `net/minecraft/world/item/Item$Properties`)
- `interfaceBinary` is the binary representation of an interface to inject (e.g. `com/example/examplemod/MyInjectedInterface`)

The interfaces can have generic parameter declarations. Assuming the target class `EntityType` has a `T` generic parameter,
we could implement `Supplier<T>` using `java/util/function/Supplier<T>`.

> [!NOTE]  
> Generics are *copied verbatim*. If you need the generics to reference a class, please use its fully qualified name (e.g. `java/util/function/Supplier<java.util.concurrent.atomic.AtomicInteger>`). As an exception to this rule, inner classes should be separated by `$` (e.g. `java/util/function/Supplier<java.util.Map$Entry>`).

### Custom transformers
Third parties can use JST to implement their source file own transformations.  
To do so, you can depend on the `net.neoforged.jst:jst-api` artifact, and implement the `net.neoforged.jst.api.SourceTransformerPlugin` service:
- the `getName` method returns the unique CLI identifier of the transformer. It will generate `--[no]-enable-{name}` CLI options
- the `createTransformer` method creates a `SourceTransformer` that will handle the replacements. The transformer will also be given to picocli to intercept custom CLI arguments

To create the executable jar with your custom transformer, you should shadow the `net.neoforged.jst:jst-cli` artifact and its dependencies, and set the main class to `net.neoforged.jst.cli.Main`.

## Usage

Note that this tool is not intended to be run by users directly. Rather it is integrated into
the [NeoGradle](https://github.com/neoforged/NeoGradle) build process.

It can be invoked as a standalone executable Jar-File. Java 21 is required.

```
Usage: jst [-hV] [--debug] [--in-format=<inputFormat>] [--libraries-list=<librariesList>]
           [--max-queue-depth=<maxQueueDepth>] [--out-format=<outputFormat>]
           [--problems-report=<problemsReport>] [--classpath=<addToClasspath>]...
           [--ignore-prefix=<ignoredPrefixes>]... [--enable-parchment
           --parchment-mappings=<mappingsPath> [--[no-]parchment-javadoc]
           [--parchment-conflict-prefix=<conflictPrefix>]] [--enable-accesstransformers
           --access-transformer=<atFiles> [--access-transformer=<atFiles>]...
           [--access-transformer-validation=<validation>]] [--enable-interface-injection
           [--interface-injection-stubs=<stubOut>]
           [--interface-injection-marker=<annotationMarker>]
           [--interface-injection-data=<paths>]...] [--enable-unpick [--unpick-data=<paths>]...]
           INPUT OUTPUT
      INPUT                Path to a single Java-file, a source-archive or a folder containing the
                             source to transform.
      OUTPUT               Path to where the resulting source should be placed.
      --classpath=<addToClasspath>
                           Additional classpath entries to use. Is combined with --libraries-list.
      --debug              Print additional debugging information
  -h, --help               Show this help message and exit.
      --ignore-prefix=<ignoredPrefixes>
                           Do not apply transformations to paths that start with any of these
                             prefixes.
      --in-format=<inputFormat>
                           Specify the format of INPUT explicitly. AUTO (the default) performs
                             auto-detection. Other options are SINGLE_FILE for Java files, ARCHIVE
                             for source jars or zips, and FOLDER for folders containing Java code.
      --libraries-list=<librariesList>
                           Specifies a file that contains a path to an archive or directory to add
                             to the classpath on each line.
      --max-queue-depth=<maxQueueDepth>
                           When both input and output support ordering (archives), the transformer
                             will try to maintain that order. To still process items in parallel, a
                             queue is used. Larger queue depths lead to higher memory usage.
      --out-format=<outputFormat>
                           Specify the format of OUTPUT explicitly. Allows the same options as
                             --in-format.
      --problems-report=<problemsReport>
                           Write problems to this report file.
  -V, --version            Print version information and exit.
Plugin - parchment
      --enable-parchment   Enable parchment
      --parchment-conflict-prefix=<conflictPrefix>
                           Apply the prefix specified if a Parchment parameter name conflicts with
                             existing variable names
      --[no-]parchment-javadoc
                           Whether Parchment javadocs should be applied
      --parchment-mappings=<mappingsPath>
                           The location of the Parchment mappings file
Plugin - accesstransformers
      --access-transformer=<atFiles>

      --access-transformer-validation=<validation>
                           The level of validation to use for ats
      --enable-accesstransformers
                           Enable accesstransformers
Plugin - interface-injection
      --enable-interface-injection
                           Enable interface-injection
      --interface-injection-data=<paths>
                           The paths to read interface injection JSON files from
      --interface-injection-marker=<annotationMarker>
                           The name (binary representation) of an annotation to use as a marker for
                             injected interfaces
      --interface-injection-stubs=<stubOut>
                           The path to a zip to save interface stubs in
Plugin - unpick
      --enable-unpick      Enable unpick
      --unpick-data=<paths>
                           The paths to read unpick definition files from
```

## Licenses

The source code in this repository is licensed under
the [LGPL 2.1](http://www.gnu.org/licenses/old-licenses/lgpl-2.1.txt).

Most of the heavy lifting is done by third party libraries such as
the [IntelliJ platform](https://github.com/JetBrains/intellij-community)
or [Mapping IO](https://github.com/FabricMC/mapping-io), which are under different licenses. Please refer to these
projects and keep in mind that the standalone executable tool will contain code from these projects.
