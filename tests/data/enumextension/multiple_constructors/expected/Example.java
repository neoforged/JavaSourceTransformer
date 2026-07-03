import com.example.Stub;

public enum Example {
    EXTENSION1((int) Stub.getParameter(0, int.class)),
    EXTENSION2((long) Stub.getParameter(0, long.class));
    private Example(int i) {}
    private Example(long j) {}
}
