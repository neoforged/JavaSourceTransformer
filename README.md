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

## Usage

Note that this tool is not intended to be run by users directly. Rather it is integrated into
the [NeoGradle](https://github.com/neoforged/NeoGradle) build process.

It can be invoked as a standalone executable Jar-File. Java 17 is required.

```
Usage: jst [-hV] [--in-format=<inputFormat>] [--libraries-list=<librariesList>]
           [--max-queue-depth=<maxQueueDepth>] [--out-format=<outputFormat>]
           [--classpath=<addToClasspath>]... [--enable-parchment
           --parchment-mappings=<mappingsPath> [--[no-]parchment-javadoc]
           [--parchment-conflict-prefix=<conflictPrefix>]] [--enable-accesstransformers
           --access-transformer=<atFiles> [--access-transformer=<atFiles>]...
           [--access-transformer-validation=<validation>]] INPUT OUTPUT
      INPUT                Path to a single Java-file, a source-archive or a folder containing the
                             source to transform.
      OUTPUT               Path to where the resulting source should be placed.
      --classpath=<addToClasspath>
                           Additional classpath entries to use. Is combined with --libraries-list.
  -h, --help               Show this help message and exit.
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
```

## Licenses

The source code in this repository is licensed under
the [LGPL 2.1](http://www.gnu.org/licenses/old-licenses/lgpl-2.1.txt).

Most of the heavy lifting is done by third party libraries such as
the [IntelliJ platform](https://github.com/JetBrains/intellij-community)
or [Mapping IO](https://github.com/FabricMC/mapping-io), which are under different licenses. Please refer to these
projects and keep in mind that the standalone executable tool will contain code from these projects.
