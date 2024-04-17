package pkg;
class Outer {
    void m(InnerClass p_1) {
        System.out.println(p_1);
        new Object() {
            void m(int p_2) {}
        }
    }
    class InnerClass {
        void m(SamePkgClass p_1) {
            class MethodClass {
                void m(DefaultPkgClass p_2) {
                    System.out.println(p_1 + " " + p_2);
                }
            }
        }
    }
}