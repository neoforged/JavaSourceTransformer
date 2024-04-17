public class TestClass {
    public TestClass(int p) {}
    public void instanceMethod(int p) {}
    public static void staticMethod(int p) {}
    public static void staticMethodWithLongAndDouble(int p1, long p2, double p3, float p4) {}
    public class InnerClass {
        public InnerClass(int p) {}
        public void instanceMethod(int p) {}
        public static void staticMethod(int p) {}
        public static class InnerStaticInnerClass {
            public InnerStaticInnerClass(int p) {}
            public void instanceMethod(int p) {}
            public static void staticMethod(int p) {}
        }
    }
    public static class StaticInnerClass {
        public StaticInnerClass(int p) {}
        public void instanceMethod(int p) {}
        public static void staticMethod(int p) {}
    }
}
