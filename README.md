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
Arguments:
  --in <input-file>      Path to input source-jar
  --out <output-file>    Path where new source-jar will be written
  --names <names-file>   Path to Parchment mappings (JSON or ZIP) or merged TSRG2-mappings
  --skip-javadoc         Don't apply Javadocs
  --queue-depth <depth>  How many source files to wait for in parallel. 0 for synchronous processing.
                         0 for synchronous processing. Default is 50.
  --help                 Print help
```

## Licenses

The source code in this repository is licensed under
the [LGPL 2.1](http://www.gnu.org/licenses/old-licenses/lgpl-2.1.txt).

Most of the heavy lifting is done by third party libraries such as
the [IntelliJ platform](https://github.com/JetBrains/intellij-community)
or [Mapping IO](https://github.com/FabricMC/mapping-io), which are under different licenses. Please refer to these
projects and keep in mind that the standalone executable tool will contain code from these projects.
