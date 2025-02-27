package net.neoforged.jst.api;

import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.UnmodifiableView;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * A replacer linked to a {@link PsiFile} will run and collect replacements after all {@link SourceTransformer transformers} have processed the file.
 */
public interface PostProcessReplacer {
    Key<Map<Class<?>, PostProcessReplacer>> REPLACERS = Key.create("jst.post_process_replacers");

    /**
     * Process replacements in the file after {@link SourceTransformer transformers} have processed it.
     */
    void process(Replacements replacements);

    @UnmodifiableView
    static Map<Class<?>, PostProcessReplacer> getReplacers(PsiFile file) {
        var rep = file.getUserData(REPLACERS);
        return rep == null ? Map.of() : Collections.unmodifiableMap(rep);
    }

    static <T extends PostProcessReplacer> T getOrCreateReplacer(PsiFile file, Class<T> type, Function<PsiFile, T> creator) {
        var rep = file.getUserData(REPLACERS);
        if (rep == null) {
            rep = new ConcurrentHashMap<>();
            file.putUserData(REPLACERS, rep);
        }
        //noinspection unchecked
        return (T)rep.computeIfAbsent(type, k -> creator.apply(file));
    }
}
