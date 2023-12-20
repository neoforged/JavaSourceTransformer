import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PsiHelperTest {
    static IntelliJEnvironment ijEnv;

    @BeforeAll
    static void setUp() throws IOException {
        ijEnv = new IntelliJEnvironment();
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
        void testMethodParameterIndex() {
            var firstParam = ctor.getParameterList().getParameter(0);
            int index = PsiHelper.getBinaryIndex(firstParam, 0);
            // Binary parameters are:
            // 0) this
            // 1) first method parameter
            assertEquals(1, index);
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
        void testMethodParameterIndex() {
            var firstParam = ctor.getParameterList().getParameter(0);
            int index = PsiHelper.getBinaryIndex(firstParam, 0);
            // Binary parameters are:
            // 0) first method parameter
            assertEquals(0, index);
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
        void testMethodParameterIndex() {
            var firstParam = ctor.getParameterList().getParameter(0);
            int index = PsiHelper.getBinaryIndex(firstParam, 0);
            // Binary parameters are:
            // 0) this
            // 1) enum literal name
            // 2) enum literal ordinal
            // 3) first method parameter
            assertEquals(3, index);
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
        void testMethodParameterIndex() {
            var firstParam = ctor.getParameterList().getParameter(0);
            int index = PsiHelper.getBinaryIndex(firstParam, 0);
            // Binary parameters are:
            // 0) this
            // 1) outer class pointer
            // 2) first method parameter
            assertEquals(2, index);
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
        void testMethodParameterIndex() {
            var firstParam = ctor.getParameterList().getParameter(0);
            int index = PsiHelper.getBinaryIndex(firstParam, 0);
            // Binary parameters are:
            // 0) this
            // 1) first method parameter
            assertEquals(1, index);
        }
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
