package net.neoforged.jst.cli;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import net.neoforged.jst.api.ImportHelper;
import net.neoforged.jst.api.Logger;
import net.neoforged.jst.api.Replacements;
import net.neoforged.jst.cli.intellij.IntelliJEnvironmentImpl;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ImportHelperTest {
    static IntelliJEnvironmentImpl ijEnv;

    @BeforeAll
    static void setUp() throws IOException {
        ijEnv = new IntelliJEnvironmentImpl(new Logger(null, null));
        ijEnv.addCurrentJdkToClassPath();
    }

    @AfterAll
    static void tearDown() throws IOException {
        ijEnv.close();
    }

    @Test
    public void testSimpleImports() {
        var helper = getImportHelper("""
import java.util.Collection;
import java.lang.annotation.Retention;
import java.util.concurrent.atomic.AtomicReference;""");

        assertFalse(helper.canImport("Collection"), "Collection can wrongly be imported");
        assertFalse(helper.canImport("Retention"), "Retention can wrongly be imported");
        assertFalse(helper.canImport("AtomicReference"), "AtomicReference can wrongly be imported");

        assertTrue(helper.canImport("MyRandomClass"), "Cannot import a non-reserved name");
    }

    @Test
    public void testWildcardImports() {
        var helper = getImportHelper("""
import java.util.concurrent.*;""");

        assertFalse(helper.canImport("Future"), "Future can wrongly be imported");
        assertFalse(helper.canImport("Executor"), "Executor can wrongly be imported");

        assertTrue(helper.canImport("ThisWillNotExist"), "Cannot import a non-reserved name");
    }

    @Test
    public void testStaticImports() {
        var helper = getImportHelper("""
import static java.util.Spliterators.emptyDoubleSpliterator;
import static java.util.Collections.*;""");

        assertFalse(helper.canImport("emptyDoubleSpliterator"), "emptyDoubleSpliterator can wrongly be imported");

        assertFalse(helper.canImport("min"), "min can wrongly be imported");
        assertFalse(helper.canImport("checkedSortedMap"), "checkedSortedMap can wrongly be imported");
        assertFalse(helper.canImport("EMPTY_LIST"), "EMPTY_LIST can wrongly be imported");

        assertTrue(helper.canImport("ThisWillNotExist"), "Cannot import a non-reserved name");
    }

    @Test
    void testReplace() {
        var file = parseSingleFile("""
package java.lang.annotation;

import java.util.*;

class MyClass {
}""");

        var helper = ImportHelper.get(file);

        assertEquals("HelloWorld", helper.importClass("com.hello.world.HelloWorld"));

        assertEquals("Annotation", helper.importClass("java.lang.annotation.Annotation"));
        assertEquals("com.hello.world.Annotation", helper.importClass("com.hello.world.Annotation"));

        assertEquals("List", helper.importClass("java.util.List"));
        assertEquals("com.hello.world.List", helper.importClass("com.hello.world.List"));

        assertEquals("Thing", helper.importClass("a.b.c.Thing"));

        var rep = new Replacements();
        helper.process(rep);

        assertThat(rep.apply(file.getText()))
                .isEqualToNormalizingNewlines("""
                        package java.lang.annotation;
                                                
                        import java.util.*;
                        import a.b.c.Thing;
                        import com.hello.world.HelloWorld;
                                                
                        class MyClass {
                        }""");
    }

    private ImportHelper getImportHelper(@Language("JAVA") String javaCode) {
        var file = parseSingleFile(javaCode);
        return new ImportHelper(file);
    }

    private PsiJavaFile parseSingleFile(@Language("JAVA") String javaCode) {
        return parseSingleElement(javaCode, PsiJavaFile.class);
    }

    private <T extends PsiElement> T parseSingleElement(@Language("JAVA") String javaCode, Class<T> type) {
        var file = ijEnv.parseFileFromMemory("Test.java", javaCode);

        var elements = PsiTreeUtil.collectElementsOfType(file, type);
        assertEquals(1, elements.size());
        return elements.iterator().next();
    }
}
