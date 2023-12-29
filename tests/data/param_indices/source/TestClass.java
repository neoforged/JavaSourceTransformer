public class TestClass {
    public TestClass(int p) {}
    public void instanceMethod(int p) {}
    public static void staticMethod(int p) {}
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
