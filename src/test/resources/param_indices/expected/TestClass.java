public class TestClass {
    public TestClass(int mapped) {}
    public void instanceMethod(int mapped) {}
    public static void staticMethod(int mapped) {}
    public class InnerClass {
        public InnerClass(int mapped) {}
        public void instanceMethod(int mapped) {}
        public static void staticMethod(int mapped) {}
        public static class InnerStaticInnerClass {
            public InnerStaticInnerClass(int mapped) {}
            public void instanceMethod(int mapped) {}
            public static void staticMethod(int mapped) {}
        }
    }
    public static class StaticInnerClass {
        public StaticInnerClass(int mapped) {}
        public void instanceMethod(int mapped) {}
        public static void staticMethod(int mapped) {}
    }
}
