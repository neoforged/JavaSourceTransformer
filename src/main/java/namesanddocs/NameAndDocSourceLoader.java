package namesanddocs;

import namesanddocs.mappingio.TreeData;
import namesanddocs.parchment.ParchmentDatabase;
import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.format.MappingFormat;
import net.fabricmc.mappingio.tree.MemoryMappingTree;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;

public final class NameAndDocSourceLoader {
    private NameAndDocSourceLoader() {
    }

    public static NamesAndDocsDatabase load(Path path) throws IOException {
        var lowerFilename = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (lowerFilename.endsWith(".zip")) {
            return ParchmentDatabase.load(path);
        } else if (lowerFilename.endsWith(".tsrg")) {
            var tree = new MemoryMappingTree(true);
            MappingReader.read(path, MappingFormat.TSRG_2_FILE, tree);

            return new TreeData(tree);
        } else {
            throw new IllegalArgumentException("Don't know how to load " + path);
        }
    }

}

