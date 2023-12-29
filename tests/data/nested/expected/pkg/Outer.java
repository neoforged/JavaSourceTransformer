package pkg;
class Outer {
    void m(InnerClass innerClass) {
        System.out.println(innerClass);
        new Object() {
            void m(int mapped) {}
        }
    }
    class InnerClass {
        void m(SamePkgClass samePkgClass) {
            class MethodClass {
                void m(DefaultPkgClass defaultPkgClass) {
                    System.out.println(samePkgClass + " " + defaultPkgClass);
                }
            }
        }
    }
}