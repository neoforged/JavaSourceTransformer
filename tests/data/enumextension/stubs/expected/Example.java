import com.example.Stub1;
import com.example.Stub2;

public enum Example {
    EXISTING(0),
    A((int) RootStub.PROXY.getParameter(0)),
    B((int) Stub1.PROXY.getParameter(0)),
    C((int) Stub2.PROXY.getParameter(0));
    Example(int parameter) {}
}
