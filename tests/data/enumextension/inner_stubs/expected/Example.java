import com.example.Stubs.Inner;
import com.example.Stubs.Inner.SubInner;

public enum Example {
    EXISTING(0),
    A(Inner.A.getParameter(0)),
    B(Inner.B.getParameter(0)),
    C(SubInner.C.getParameter(0));
    Example(int parameter) {}
}
