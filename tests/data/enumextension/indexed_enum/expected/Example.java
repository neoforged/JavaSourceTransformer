import com.markers.IndexedEnum;

@IndexedEnum(value = 1)
public enum Example {
    EXISTING("x", 0, "z"),
    EXTENSION("a", 1, "c");
    Example(String p0, int p1, String p2) {}
}
