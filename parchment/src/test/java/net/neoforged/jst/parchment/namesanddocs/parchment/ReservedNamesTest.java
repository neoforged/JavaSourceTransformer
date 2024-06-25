package net.neoforged.jst.parchment.namesanddocs.parchment;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import net.neoforged.jst.api.Logger;
import net.neoforged.jst.cli.intellij.IntelliJEnvironmentImpl;
import net.neoforged.jst.parchment.ReservedNamesCollector;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class ReservedNamesTest {
    static IntelliJEnvironmentImpl ijEnv;

    @BeforeAll
    static void setUp() throws IOException {
        ijEnv = new IntelliJEnvironmentImpl(new Logger(null, null));
    }

    @AfterAll
    static void tearDown() throws IOException {
        ijEnv.close();
    }

    @Test
    void testSimpleDeclarations() {
        var method = parseSingleMethod("""
                class Main {
                    void run() {
                        String declared = "a";
                        int declaredInt = 1;
                    }
                }""");
        assertThat(collectReferences(method))
                .containsExactlyInAnyOrder("declared", "declaredInt");
    }

    @Test
    void testLocalReferences() {
        var method = parseSingleMethod("""
                class Main {
                    int qualified, nonqualified;
                    
                    void run() {
                        this.qualified = 1;
                        nonqualified = 2;
                    }
                }""");
        assertThat(collectReferences(method))
                .containsExactlyInAnyOrder("nonqualified");
    }

    @Test
    void testMethodReferences() {
        var method = parseSingleMethod("""
                class Main {
                    void run() {
                        Runnable runnable = this::run;
                    }
                }""");

        // We test that the run method reference isn't considered a reserved name
        assertThat(collectReferences(method))
                .containsExactlyInAnyOrder("runnable");
    }

    @Test
    void testLambdaParameters() {
        var method = parseSingleMethod("""
                import java.util.function.Function;
                class Main {
                    void run() {
                        Function<String, Void> func = (p1) -> {
                        };
                    }
                }""");

        assertThat(collectReferences(method))
                .containsExactlyInAnyOrder("func", "p1");
    }

    @Test
    void testLambdaLocals() {
        var method = parseSingleMethod("""
                import java.util.function.Function;
                class Main {
                    void run() {
                        Runnable run = () -> {
                            String lambdaLocal = "abc";
                        };
                    }
                }""");

        assertThat(collectReferences(method))
                .containsExactlyInAnyOrder("run", "lambdaLocal");
    }

    @Test
    void testAnonymousFields() {
        // Test that fields of anonymous classes are considered reserved names as they take priority over
        // captured locals of the parent method
        var file = parse("""
                import java.util.function.Function;
                class Main {
                    void run() {
                        new SubClass() {
                            int declaredAnon;
                        };
                    }
                    
                    static class SubClass extends SubClassParent {
                        private String directField;
                    }
                    
                    static class SubClassParent {
                        protected int inheritedField;
                    }
                }""");

        assertThat(collectReferences(file.getClasses()[0].getMethods()[0]))
                .containsExactlyInAnyOrder("directField", "inheritedField", "declaredAnon");
    }

    @Test
    void testLocalClassFields() {
        // Test that fields of local classes are considered reserved names as they take priority over
        // captured locals of the parent method
        var file = parse("""
                import java.util.function.Function;
                class Main {
                    void run() {
                        class LocalClass extends SubClass {
                            int declaredAnon;
                        }
                    }
                    
                    static class SubClass extends SubClassParent {
                        private String directField;
                    }
                    
                    static class SubClassParent {
                        protected int inheritedField;
                    }
                }""");

        assertThat(collectReferences(file.getClasses()[0].getMethods()[0]))
                .containsExactlyInAnyOrder("directField", "inheritedField", "declaredAnon");
    }

    private Set<String> collectReferences(PsiElement element) {
        var set = new HashSet<String>();
        new ReservedNamesCollector(set).visitElement(element);
        return set;
    }

    private PsiMethod parseSingleMethod(@Language("JAVA") String javaCode) {
        return parseSingleElement(javaCode, PsiMethod.class);
    }

    private PsiClass parseSingleClass(@Language("JAVA") String javaCode) {
        return parseSingleElement(javaCode, PsiClass.class);
    }

    private <T extends PsiElement> T parseSingleElement(@Language("JAVA") String javaCode, Class<T> type) {
        var file = parse(javaCode);

        var elements = PsiTreeUtil.collectElementsOfType(file, type);
        assertThat(1).describedAs("parsed elements").isEqualTo(elements.size());
        return elements.iterator().next();
    }

    private PsiJavaFile parse(@Language("JAVA") String javaCode) {
        return (PsiJavaFile) ijEnv.parseFileFromMemory("Test.java", javaCode);
    }
}
