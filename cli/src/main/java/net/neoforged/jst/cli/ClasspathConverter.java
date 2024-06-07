package net.neoforged.jst.cli;

import picocli.CommandLine;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

final class ClasspathConverter implements CommandLine.ITypeConverter<List<Path>> {
    @Override
    public List<Path> convert(String value) {
        return Arrays.stream(value.split(File.pathSeparator))
                .map(Paths::get)
                .toList();
    }
}
