import org.junit.jupiter.api.Test;

import java.util.*;

public class TestClass {
    public void m(String a1, List<String> a2, Test a3) {
    }

    // This method tests that unresolvable external references aren't simply dropped
    // in the method signature when looking up Parchment data. The IntelliJ default methods
    // would do this. This overload should not pick up data from the first method.
    public void m(String a1, List<String> a2, AnExternalClassThatDoesNotExist a4, Test a3) {
    }
}
