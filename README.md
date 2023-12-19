# Apply Parchment to Source Jar

This tool is intended to apply [Parchment Mappings](https://parchmentmc.org/) to a Source-Jar containing
Minecraft source code right before it is recompiled for use in a dev environment.

It applies method parameter names as well as Javadoc definitions using libraries
from [IntelliJ Community Edition](https://github.com/JetBrains/intellij-community).

To aid development of [NeoForge](https://github.com/neoforged/NeoForge) itself, it also supports replacing parameter
names with
SRG parameters using the merged mapping file produced during
the [NeoForm](https://github.com/neoforged/NeoForm)/[NeoGradle](https://github.com/neoforged/NeoGradle) build.

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
