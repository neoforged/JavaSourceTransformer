package example;

public class C {
    static C REGULAR = new C() { // $1
        @Override
        protected void method() {
            new C() { // $1$1
                @Override
                protected void method() { // $1$1 method()V 
                }
            };
        }
    };

    static C WILDCARD = new C() { // $2
        @Override
        protected void method() {
            new C() { // $2$1
                @Override
                protected void method() {
                    new C() { // $2$1$1
                        @Override
                        protected void method() {
                        }
                    };
                }

                @Override
                protected void method2() {
                    new C() { // $2$1$2
                        @Override
                        protected void method2() {
                        }
                    };
                }
            };
        }

        @Override
        protected void method2() {
        }
    };

    protected void method() {
    }

    protected void method2() {
    }
}
