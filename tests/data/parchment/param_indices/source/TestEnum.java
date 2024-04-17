public enum TestEnum {
    // Enum constructors take two hidden arguments (the constant name and ordinal)
    // Which are part of the method signature. This is in addition to the this pointer.
    private TestEnum(int p) {
    }
}
