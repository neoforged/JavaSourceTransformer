import com.example.Stubs.Inner;
import com.example.Stubs.Inner.SubInner;

public enum Example {
    EXISTING(0),
    A((int) Inner.A.getParameter(0)),
    B((int) Inner.B.getParameter(0)),
    C((int) SubInner.C.getParameter(0));
    Example(int parameter) {}
}
