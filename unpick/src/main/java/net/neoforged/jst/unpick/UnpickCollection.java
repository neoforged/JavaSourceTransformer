package net.neoforged.jst.unpick;

import com.intellij.lang.jvm.JvmModifier;
import com.intellij.openapi.util.Key;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypes;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.ClassUtil;
import com.intellij.util.containers.MultiMap;
import daomephsta.unpick.constantmappers.datadriven.tree.DataType;
import daomephsta.unpick.constantmappers.datadriven.tree.GroupDefinition;
import daomephsta.unpick.constantmappers.datadriven.tree.GroupFormat;
import daomephsta.unpick.constantmappers.datadriven.tree.GroupScope;
import daomephsta.unpick.constantmappers.datadriven.tree.Literal;
import daomephsta.unpick.constantmappers.datadriven.tree.TargetField;
import daomephsta.unpick.constantmappers.datadriven.tree.TargetMethod;
import daomephsta.unpick.constantmappers.datadriven.tree.expr.BinaryExpression;
import daomephsta.unpick.constantmappers.datadriven.tree.expr.CastExpression;
import daomephsta.unpick.constantmappers.datadriven.tree.expr.Expression;
import daomephsta.unpick.constantmappers.datadriven.tree.expr.FieldExpression;
import daomephsta.unpick.constantmappers.datadriven.tree.expr.LiteralExpression;
import daomephsta.unpick.constantmappers.datadriven.tree.expr.ParenExpression;
import daomephsta.unpick.constantmappers.datadriven.tree.expr.UnaryExpression;
import net.neoforged.jst.api.PsiHelper;
import net.neoforged.jst.api.TransformContext;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class UnpickCollection {
    private static final Key<Optional<TargetMethod>> UNPICK_DEFINITION = Key.create("unpick.method_definition");
    private static final Key<TargetField> UNPICK_FIELD_TARGET = Key.create("unpick.field_target");

    private final Set<String> possibleTargetNames = new HashSet<>();
    private final MultiMap<String, Group> groups;

    private final List<Group> global;

    private final MultiMap<String, Group> byPackage;
    private final MultiMap<String, Group> byClass;

    private final MultiMap<PsiMethod, Group> methodScopes;

    // This list only exists to keep the base elements in memory and prevent them from being GC'd and therefore losing their user data
    // JavaPsiFacade#findClass uses a soft key and soft value map
    @SuppressWarnings({"FieldCanBeLocal", "MismatchedQueryAndUpdateOfCollection"})
    private final List<PsiElement> baseElements;

    public UnpickCollection(TransformContext context, Map<TypedKey, List<GroupDefinition>> groups, List<TargetField> fields, List<TargetMethod> methods) {
        this.groups = new MultiMap<>(new HashMap<>(groups.size()));

        var facade = context.environment().getPsiFacade();
        var project = context.environment().getPsiManager().getProject();

        var projectScope = GlobalSearchScope.projectScope(project);

        global = new ArrayList<>();

        byPackage = new MultiMap<>();
        byClass = new MultiMap<>();

        methodScopes = new MultiMap<>(new IdentityHashMap<>());
        baseElements = new ArrayList<>();

        groups.forEach((key, defs) -> {
            for (GroupDefinition def : defs) {
                var gr = Group.create(def, facade, projectScope);
                if (key.isGlobal()) {
                    global.add(gr);
                } else {
                    this.groups.putValue(key.name(), gr);

                    for (var scope : def.scopes()) {
                        switch (scope) {
                            case GroupScope.Package(var packageName) -> byPackage.putValue(packageName, gr);
                            case GroupScope.Class(var cls) -> byClass.putValue(cls, gr);
                            case GroupScope.Method(var className, var method, var desc) -> {
                                var cls = PsiHelper.findClass(facade, className, projectScope);
                                if (cls == null) return;

                                for (PsiMethod clsMethod : cls.getMethods()) {
                                    if (clsMethod.getName().equals(method) && PsiHelper.getBinaryMethodSignature(clsMethod).equals(desc)) {
                                        methodScopes.putValue(clsMethod, gr);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        });

        for (var field : fields) {
            var cls = PsiHelper.findClass(facade, field.className(), projectScope);
            if (cls == null) continue;

            var fld = cls.findFieldByName(field.fieldName(), true);
            if (fld != null) {
                fld.putUserData(UNPICK_FIELD_TARGET, field);
                baseElements.add(fld);
            }
        }

        for (var method : methods) {
            var cls = PsiHelper.findClass(facade, method.className(), projectScope);
            if (cls == null) continue;

            possibleTargetNames.add(method.methodName());

            for (PsiMethod clsMethod : cls.getMethods()) {
                if (clsMethod.getName().equals(method.methodName()) && PsiHelper.getBinaryMethodSignature(clsMethod).equals(method.methodDesc())) {
                    clsMethod.putUserData(UNPICK_DEFINITION, Optional.of(method));
                    baseElements.add(clsMethod);
                }
            }
        }
    }

    public Collection<Group> getClassContext(PsiClass cls) {
        var clsName = ClassUtil.getJVMClassName(cls);
        if (clsName != null) {
            return byClass.get(clsName);
        }
        return List.of();
    }


    public Collection<Group> getPackageContext(PsiJavaFile file) {
        return byPackage.get(file.getPackageName());
    }

    public Collection<Group> getMethodContext(PsiMethod method) {
        return methodScopes.get(method);
    }

    public Collection<Group> getGlobalContext() {
        return global;
    }

    @SuppressWarnings("OptionalAssignedToNull")
    @Nullable
    public TargetMethod getDefinitionsFor(PsiMethod method) {
        if (!possibleTargetNames.contains(method.getName())) return null;
        var data = method.getUserData(UNPICK_DEFINITION);
        if (data == null) {
            synchronized (this) {
                if (method.getParent() instanceof PsiClass cls) {
                    for (PsiClass iface : cls.getInterfaces()) {
                        var met = iface.findMethodBySignature(method, true);
                        if (met != null) {
                            var parent = getDefinitionsFor(met);
                            if (parent != null) {
                                data = Optional.of(parent);
                                break;
                            }
                        }
                    }

                    if (data == null && cls.getSuperClass() != null) {
                        var met = cls.getSuperClass().findMethodBySignature(method, true);
                        if (met != null) {
                            var parent = getDefinitionsFor(met);
                            if (parent != null) {
                                data = Optional.of(parent);
                            }
                        }
                    }
                }

                if (data == null) data = Optional.empty();
                method.putUserData(UNPICK_DEFINITION, data);
            }
        }
        return data.orElse(null);
    }

    public Collection<Group> getGroups(String id) {
        return groups.get(id);
    }

    public record Group(
            DataType data,
            boolean strict,
            boolean flag,
            @Nullable GroupFormat format,
            Map<Object, Expression> constants
    ) {
        public static Group create(GroupDefinition def, JavaPsiFacade facade, GlobalSearchScope scope) {
            var constants = HashMap.<Object, Expression>newHashMap(def.constants().size());
            for (Expression constant : def.constants()) {
                var value = resolveConstant(constant, facade, scope);
                constants.put(cast(value, def.dataType()), constant);
            }
            return new Group(
                    def.dataType(),
                    def.strict(),
                    def.flags(),
                    def.format(),
                    constants
            );
        }

        private static Object resolveConstant(Expression expression, JavaPsiFacade facade, GlobalSearchScope scope) {
            if (expression instanceof FieldExpression fieldEx) {
                var clazz = PsiHelper.findClass(facade, fieldEx.className, scope);
                if (clazz != null) {
                    for (PsiField field : clazz.getAllFields()) {
                        if (fieldEx.isStatic != field.hasModifier(JvmModifier.STATIC)) continue;
                        if (fieldEx.fieldType != null && !sameType(fieldEx.fieldType, field.getType())) continue;
                        if (fieldEx.fieldName.equals(field.getName())) {
                            return field.computeConstantValue();
                        }
                    }
                }
                throw new IllegalArgumentException("Cannot find field named " + fieldEx.className + " of type " + fieldEx.fieldType + " in class " + fieldEx.className);
            } else if (expression instanceof LiteralExpression literalExpression) {
                return switch (literalExpression.literal) {
                    case Literal.Character(var ch) -> ch;
                    case Literal.Integer i -> i.value();
                    case Literal.Long l -> l.value();
                    case Literal.Float(var f) -> f;
                    case Literal.Double(var d) -> d;
                    case Literal.String(var s) -> s;
                };
            } else if (expression instanceof ParenExpression parenExpression) {
                return resolveConstant(parenExpression.expression, facade, scope);
            } else if (expression instanceof CastExpression castExpression) {
                return cast(resolveConstant(castExpression.operand, facade, scope), castExpression.castType);
            } else if (expression instanceof UnaryExpression unaryExpression) {
                var value = (Number) resolveConstant(unaryExpression.operand, facade, scope);
                return switch (unaryExpression.operator) {
                    case NEGATE -> NumberType.TYPES.get(value.getClass()).negate(value);
                    case BIT_NOT -> value instanceof Long ? ~value.longValue() : ~value.intValue();
                };
            } else if (expression instanceof BinaryExpression binaryExpression) {
                var lhs = resolveConstant(binaryExpression.lhs, facade, scope);
                var rhs = resolveConstant(binaryExpression.rhs, facade, scope);

                if (lhs instanceof Number l && rhs instanceof Number r) {
                    var type = NumberType.widest(NumberType.TYPES.get(l.getClass()), NumberType.TYPES.get(r.getClass()));
                    return switch (binaryExpression.operator) {
                        case ADD -> type.add(l, r);
                        case DIVIDE -> type.divide(l, r);
                        case MODULO -> type.modulo(l, r);
                        case MULTIPLY -> type.multiply(l, r);
                        case SUBTRACT -> type.subtract(l, r);

                        case BIT_AND -> type.and(l, r);
                        case BIT_OR -> type.or(l, r);
                        case BIT_XOR -> type.xor(l, r);

                        case BIT_SHIFT_LEFT -> type.lshift(l, r);
                        case BIT_SHIFT_RIGHT -> type.rshift(l, r);
                        case BIT_SHIFT_RIGHT_UNSIGNED -> type.rshiftUnsigned(l, r);
                    };
                }

                if ((lhs instanceof String || rhs instanceof String) && binaryExpression.operator == BinaryExpression.Operator.ADD) {
                    return lhs.toString() + rhs.toString();
                }

                throw new IllegalArgumentException("Cannot resolve expression: " + binaryExpression + ". Operands of type " + lhs.getClass() + " and " + rhs.getClass() + " do not support operator " + binaryExpression.operator);
            }

            throw new IllegalArgumentException("Unknown Expression of type " + expression.getClass() + ": " + expression);
        }

        private static Object cast(Object in, DataType type) {
            return switch (type) {
                case BYTE -> ((Number) in).byteValue();
                case CHAR -> Character.valueOf((char)((Number) in).shortValue());
                case SHORT -> ((Number) in).shortValue();
                case INT -> ((Number) in).intValue();
                case LONG -> ((Number) in).longValue();
                case FLOAT -> ((Number) in).floatValue();
                case DOUBLE -> ((Number) in).doubleValue();
                case CLASS -> (Class<?>) in;
                case STRING -> in.toString();
            };
        }

        private static boolean sameType(DataType type, PsiType fieldType) {
            return switch (type) {
                case BYTE -> fieldType.equals(PsiTypes.byteType());
                case CHAR -> fieldType.equals(PsiTypes.charType());
                case SHORT -> fieldType.equals(PsiTypes.shortType());
                case INT -> fieldType.equals(PsiTypes.intType());
                case LONG -> fieldType.equals(PsiTypes.longType());
                case FLOAT -> fieldType.equals(PsiTypes.floatType());
                case DOUBLE -> fieldType.equals(PsiTypes.doubleType());
                case CLASS -> ((PsiClassType) fieldType).resolve().getQualifiedName().equals("java.lang.Class");
                case STRING -> ((PsiClassType) fieldType).resolve().getQualifiedName().equals("java.lang.String");
            };
        }
    }

    public record TypedKey(DataType type, List<GroupScope> scopes, @Nullable String name) {
        public boolean isGlobal() {
            return name == null && scopes.isEmpty();
        }
    }
}
