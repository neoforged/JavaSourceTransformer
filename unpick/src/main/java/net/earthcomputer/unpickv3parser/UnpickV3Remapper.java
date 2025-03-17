package net.earthcomputer.unpickv3parser;

import net.earthcomputer.unpickv3parser.tree.DataType;
import net.earthcomputer.unpickv3parser.tree.GroupConstant;
import net.earthcomputer.unpickv3parser.tree.GroupDefinition;
import net.earthcomputer.unpickv3parser.tree.GroupScope;
import net.earthcomputer.unpickv3parser.tree.TargetField;
import net.earthcomputer.unpickv3parser.tree.TargetMethod;
import net.earthcomputer.unpickv3parser.tree.UnpickV3Visitor;
import net.earthcomputer.unpickv3parser.tree.expr.Expression;
import net.earthcomputer.unpickv3parser.tree.expr.ExpressionTransformer;
import net.earthcomputer.unpickv3parser.tree.expr.FieldExpression;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Remaps all class, field, and method names in a .unpick v3 file. Visitor methods will be called on the downstream
 * visitor with the remapped names.
 */
public class UnpickV3Remapper extends UnpickV3Visitor {
    private final UnpickV3Visitor downstream;
    private final Map<String, List<String>> classesInPackage;
    private final Map<String, String> classMappings;
    private final Map<FieldKey, String> fieldMappings;
    private final Map<MethodKey, String> methodMappings;

    /**
     * Warning: class names use "." format, not "/" format. {@code classesInPackage} should contain all the classes in
     * each package, including unmapped ones. The classes in this map are unqualified by the package name (because the
     * package name is already in the key of the map entry).
     */
    public UnpickV3Remapper(
        UnpickV3Visitor downstream,
        Map<String, List<String>> classesInPackage,
        Map<String, String> classMappings,
        Map<FieldKey, String> fieldMappings,
        Map<MethodKey, String> methodMappings
    ) {
        this.downstream = downstream;
        this.classesInPackage = classesInPackage;
        this.classMappings = classMappings;
        this.fieldMappings = fieldMappings;
        this.methodMappings = methodMappings;
    }

    @Override
    public void visitGroupDefinition(GroupDefinition groupDefinition) {
        GroupScope oldScope = groupDefinition.scope;
        List<GroupScope> scopes;
        if (oldScope instanceof GroupScope.Global) {
            scopes = Collections.singletonList(oldScope);
        } else if (oldScope instanceof GroupScope.Package) {
            String pkg = ((GroupScope.Package) oldScope).packageName;
            scopes = classesInPackage.getOrDefault(pkg, Collections.emptyList()).stream()
                .map(cls -> new GroupScope.Class(mapClassName(pkg + "." + cls)))
                .collect(Collectors.toList());
        } else if (oldScope instanceof GroupScope.Class) {
            scopes = Collections.singletonList(new GroupScope.Class(mapClassName(((GroupScope.Class) oldScope).className)));
        } else if (oldScope instanceof GroupScope.Method) {
            GroupScope.Method methodScope = (GroupScope.Method) oldScope;
            String className = mapClassName(methodScope.className);
            String methodName = mapMethodName(methodScope.className, methodScope.methodName, methodScope.methodDesc);
            String methodDesc = mapDescriptor(methodScope.methodDesc);
            scopes = Collections.singletonList(new GroupScope.Method(className, methodName, methodDesc));
        } else {
            throw new AssertionError("Unknown group scope type: " + oldScope.getClass().getName());
        }

        List<GroupConstant> constants = groupDefinition.constants.stream()
            .map(constant -> new GroupConstant(constant.key, constant.value.transform(new ExpressionRemapper(groupDefinition.dataType))))
            .collect(Collectors.toList());

        for (GroupScope scope : scopes) {
            downstream.visitGroupDefinition(new GroupDefinition(scope, groupDefinition.type, groupDefinition.strict, groupDefinition.dataType, groupDefinition.name, constants, groupDefinition.format));
        }
    }

    @Override
    public void visitTargetField(TargetField targetField) {
        String className = mapClassName(targetField.className);
        String fieldName = mapFieldName(targetField.className, targetField.fieldName, targetField.fieldDesc);
        String fieldDesc = mapDescriptor(targetField.fieldDesc);
        downstream.visitTargetField(new TargetField(className, fieldName, fieldDesc, targetField.groupName));
    }

    @Override
    public void visitTargetMethod(TargetMethod targetMethod) {
        String className = mapClassName(targetMethod.className);
        String methodName = mapMethodName(targetMethod.className, targetMethod.methodName, targetMethod.methodDesc);
        String methodDesc = mapDescriptor(targetMethod.methodDesc);
        downstream.visitTargetMethod(new TargetMethod(className, methodName, methodDesc, targetMethod.paramGroups, targetMethod.returnGroup));
    }

    private String mapClassName(String className) {
        return classMappings.getOrDefault(className, className);
    }

    private String mapFieldName(String className, String fieldName, String fieldDesc) {
        return fieldMappings.getOrDefault(new FieldKey(className, fieldName, fieldDesc), fieldName);
    }

    private String mapMethodName(String className, String methodName, String methodDesc) {
        return methodMappings.getOrDefault(new MethodKey(className, methodName, methodDesc), methodName);
    }

    private String mapDescriptor(String descriptor) {
        StringBuilder mappedDescriptor = new StringBuilder();

        int semicolonIndex = 0;
        int lIndex;
        while ((lIndex = descriptor.indexOf('L', semicolonIndex)) != -1) {
            mappedDescriptor.append(descriptor, semicolonIndex, lIndex + 1);
            semicolonIndex = descriptor.indexOf(';', lIndex);
            if (semicolonIndex == -1) {
                throw new AssertionError("Invalid descriptor: " + descriptor);
            }
            String className = descriptor.substring(lIndex + 1, semicolonIndex).replace('/', '.');
            mappedDescriptor.append(mapClassName(className).replace('.', '/'));
        }

        return mappedDescriptor.append(descriptor, semicolonIndex, descriptor.length()).toString();
    }

    public static final class FieldKey {
        public final String className;
        public final String fieldName;
        public final String fieldDesc;

        /**
         * Warning: class name uses "." format, not "/" format
         */
        public FieldKey(String className, String fieldName, String fieldDesc) {
            this.className = className;
            this.fieldName = fieldName;
            this.fieldDesc = fieldDesc;
        }

        @Override
        public int hashCode() {
            return Objects.hash(className, fieldName, fieldDesc);
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof FieldKey)) {
                return false;
            }
            FieldKey other = (FieldKey) o;
            return className.equals(other.className) && fieldName.equals(other.fieldName) && fieldDesc.equals(other.fieldDesc);
        }

        @Override
        public String toString() {
            return className + "." + fieldName + ":" + fieldDesc;
        }
    }

    public static final class MethodKey {
        public final String className;
        public final String methodName;
        public final String methodDesc;

        /**
         * Warning: class name uses "." format, not "/" format
         */
        public MethodKey(String className, String methodName, String methodDesc) {
            this.className = className;
            this.methodName = methodName;
            this.methodDesc = methodDesc;
        }

        @Override
        public int hashCode() {
            return Objects.hash(className, methodName, methodDesc);
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof MethodKey)) {
                return false;
            }
            MethodKey other = (MethodKey) o;
            return className.equals(other.className) && methodName.equals(other.methodName) && methodDesc.equals(other.methodDesc);
        }

        @Override
        public String toString() {
            return className + "." + methodName + methodDesc;
        }
    }

    private class ExpressionRemapper extends ExpressionTransformer {
        private final DataType groupDataType;

        ExpressionRemapper(DataType groupDataType) {
            this.groupDataType = groupDataType;
        }

        @Override
        public Expression transformFieldExpression(FieldExpression fieldExpression) {
            String fieldDesc;
            switch (fieldExpression.fieldType == null ? groupDataType : fieldExpression.fieldType) {
                case BYTE:
                    fieldDesc = "B";
                    break;
                case SHORT:
                    fieldDesc = "S";
                    break;
                case INT:
                    fieldDesc = "I";
                    break;
                case LONG:
                    fieldDesc = "J";
                    break;
                case FLOAT:
                    fieldDesc = "F";
                    break;
                case DOUBLE:
                    fieldDesc = "D";
                    break;
                case CHAR:
                    fieldDesc = "C";
                    break;
                case STRING:
                    fieldDesc = "Ljava/lang/String;";
                    break;
                default:
                    throw new AssertionError("Unknown data type: " + fieldExpression.fieldType);
            }

            String className = mapClassName(fieldExpression.className);
            String fieldName = mapFieldName(fieldExpression.className, fieldExpression.fieldName, fieldDesc);
            return new FieldExpression(className, fieldName, fieldExpression.fieldType, fieldExpression.isStatic);
        }
    }
}
