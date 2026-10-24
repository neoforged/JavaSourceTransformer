package example;

public class C {
    static C REGULAR = new C() { // $1
        @Override
        public void method() {
            new C() { // $1$1
                @Override
                public void method() { // $1$1 method()V 
                }
            };
        }
    };

    static C WILDCARD = new C() { // $2
        @Override
        public void method() {
            new C() { // $2$1
                @Override
                public void method() {
                    new C() { // $2$1$1
                        @Override
                        public void method() {
                        }
                    };
                }

                @Override
                public void method2() {
                    new C() { // $2$1$2
                        @Override
                        public void method2() {
                        }
                    };
                }
            };
        }

        @Override
        public void method2() {
        }
    };

    protected void method() {
    }

    protected void method2() {
    }
}
