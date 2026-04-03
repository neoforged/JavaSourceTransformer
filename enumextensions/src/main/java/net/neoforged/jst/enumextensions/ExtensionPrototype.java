package net.neoforged.jst.enumextensions;

import java.util.List;

record ExtensionPrototype(
        String name,
        String ctorDescriptor,
        EnumParameters parameters
) {
    sealed interface EnumParameters {
        record Constant(List<Object> params) implements EnumParameters {}

        record FieldReference(String owner, String fieldName) implements EnumParameters {}

        record MethodReference(String owner, String methodName) implements EnumParameters {}
    }
}
