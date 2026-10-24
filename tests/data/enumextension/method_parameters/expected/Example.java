import com.example.Stub;

public enum Example {
    EXTENSION((int) Stub.getParameter(0, int.class), (int[]) Stub.getParameter(1, int[].class), (Object) Stub.getParameter(2, Object.class), (Object[]) Stub.getParameter(3, Object[].class), (Object[][]) Stub.getParameter(4, Object[][].class));
    Example(int p0, int[] p1, Object p2, Object[] p3, Object[][] p4) {}
}
