import com.example.Stub1;
import com.example.Stub2;

public enum Example {
    EXISTING(0),
    A(RootStub.PROXY.getParameter(0)),
    B(Stub1.PROXY.getParameter(0)),
    C(Stub2.PROXY.getParameter(0));
    Example(int parameter) {}
}
