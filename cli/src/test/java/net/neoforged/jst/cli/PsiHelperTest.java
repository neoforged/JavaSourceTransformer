package net.neoforged.jst.cli;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import net.neoforged.jst.api.PsiHelper;
import net.neoforged.jst.cli.intellij.IntelliJEnvironmentImpl;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PsiHelperTest {
    static IntelliJEnvironmentImpl ijEnv;

    @BeforeAll
    static void setUp() throws IOException {
        ijEnv = new IntelliJEnvironmentImpl();
    }

    @AfterAll
    static void tearDown() throws IOException {
        ijEnv.close();
    }

    @Nested
    class AnonymousClass {
        @Test
        void testGetBinaryName() {
            var method = parseSingleMethod("""
                    class Outer {
                        private static final Object f = new Object() {
                            void m (int i) {}
                        };
                    }
                    """);

            var cn = new StringBuilder();
            PsiHelper.getBinaryClassName(method.getContainingClass(), cn);
            assertEquals("Outer$1", cn.toString());
        }
    }

    @Nested
    class InstanceMethod {
        private PsiMethod ctor = parseSingleMethod("""
                class TestClass {
                    void m(int p) {}
                }
                """);

        @Test
        void testName() {
            assertEquals("m", PsiHelper.getBinaryMethodName(ctor));
        }

        @Test
        void testSignature() {
            assertEquals("(I)V", PsiHelper.getBinaryMethodSignature(ctor));
        }

        @Test
        void testMethodParameterIndices() {
            // LVT of method should be:
            // 0) this
            // 1) first method parameter
            assertThat(PsiHelper.getParameterLvtIndices(ctor))
                    .containsExactly(1);
        }
    }

    @Nested
    class StaticMethod {
        private PsiMethod ctor = parseSingleMethod("""
                class TestClass {
                    static void m(int p) {}
                }
                """);

        @Test
        void testName() {
            assertEquals("m", PsiHelper.getBinaryMethodName(ctor));
        }

        @Test
        void testSignature() {
            assertEquals("(I)V", PsiHelper.getBinaryMethodSignature(ctor));
        }

        @Test
        void testMethodParameterIndices() {
            // Binary parameters are:
            // 0) first method parameter
            assertThat(PsiHelper.getParameterLvtIndices(ctor))
                    .containsExactly(0);
        }
    }

    @Nested
    class EnumConstructor {
        private PsiMethod ctor = parseSingleMethod("""
                enum TestEnum {
                    LITERAL;
                    TestEnum(int p) {}
                }
                """);

        @Test
        void testName() {
            assertEquals("<init>", PsiHelper.getBinaryMethodName(ctor));
        }

        @Test
        void testSignature() {
            assertEquals("(Ljava/lang/String;II)V", PsiHelper.getBinaryMethodSignature(ctor));
        }

        @Test
        void testMethodParameterIndices() {
            // Binary parameters are:
            // 0) this
            // 1) enum literal name
            // 2) enum literal ordinal
            // 3) first method parameter
            assertThat(PsiHelper.getParameterLvtIndices(ctor))
                    .containsExactly(3);
        }
    }

    @Nested
    class InnerClassConstructor {
        private PsiMethod ctor = parseSingleMethod("""
                class Outer {
                    class Inner {
                      Inner(int p) {}
                    }
                }
                """);

        @Test
        void testName() {
            assertEquals("<init>", PsiHelper.getBinaryMethodName(ctor));
        }

        @Test
        void testSignature() {
            assertEquals("(LOuter;I)V", PsiHelper.getBinaryMethodSignature(ctor));
        }

        @Test
        void testMethodParameterIndices() {
            // Binary parameters are:
            // 0) this
            // 1) outer class pointer
            // 2) first method parameter
            assertThat(PsiHelper.getParameterLvtIndices(ctor))
                    .containsExactly(2);
        }
    }

    @Nested
    class StaticInnerClassConstructor {
        private PsiMethod ctor = parseSingleMethod("""
                class Outer {
                    static class Inner {
                      Inner(int p) {}
                    }
                }
                """);

        @Test
        void testName() {
            assertEquals("<init>", PsiHelper.getBinaryMethodName(ctor));
        }

        @Test
        void testSignature() {
            assertEquals("(I)V", PsiHelper.getBinaryMethodSignature(ctor));
        }

        @Test
        void testMethodParameterIndices() {
            // Binary parameters are:
            // 0) this
            // 1) first method parameter
            assertThat(PsiHelper.getParameterLvtIndices(ctor))
                    .containsExactly(1);
        }
    }

    @Test
    void testLvtIndicesForPrimitiveTypes() {
        var m = parseSingleMethod("""
                class Outer {
                    static void m(byte p1, short p2, int p3, long p4, float p5, double p6, boolean p7) {
                    }
                }
                """);

        assertEquals("(BSIJFDZ)V", PsiHelper.getBinaryMethodSignature(m));
        assertThat(PsiHelper.getParameterLvtIndices(m))
                .containsExactly(0, 1, 2, 3, 5, 6, 8);
    }

    private PsiMethod parseSingleMethod(@Language("JAVA") String javaCode) {
        return parseSingleElement(javaCode, PsiMethod.class);
    }

    private PsiClass parseSingleClass(@Language("JAVA") String javaCode) {
        return parseSingleElement(javaCode, PsiClass.class);
    }

    private <T extends PsiElement> T parseSingleElement(@Language("JAVA") String javaCode, Class<T> type) {
        var file = ijEnv.parseFileFromMemory("Test.java", javaCode);

        var elements = PsiTreeUtil.collectElementsOfType(file, type);
        assertEquals(1, elements.size());
        return elements.iterator().next();
    }
}
