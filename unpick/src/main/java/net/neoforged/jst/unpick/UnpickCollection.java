package net.neoforged.jst.unpick;

import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.containers.MultiMap;
import net.earthcomputer.unpickv3parser.tree.DataType;
import net.earthcomputer.unpickv3parser.tree.GroupDefinition;
import net.earthcomputer.unpickv3parser.tree.GroupFormat;
import net.earthcomputer.unpickv3parser.tree.GroupScope;
import net.earthcomputer.unpickv3parser.tree.GroupType;
import net.earthcomputer.unpickv3parser.tree.Literal;
import net.earthcomputer.unpickv3parser.tree.TargetField;
import net.earthcomputer.unpickv3parser.tree.TargetMethod;
import net.earthcomputer.unpickv3parser.tree.expr.Expression;
import net.neoforged.jst.api.PsiHelper;
import net.neoforged.jst.api.TransformContext;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class UnpickCollection {
    private static final Key<Optional<TargetMethod>> UNPICK_DEFINITION = Key.create("unpick.method_definition");
    private static final Key<TargetField> UNPICK_FIELD_TARGET = Key.create("unpick.field_target");

    private final Set<String> possibleTargetNames = new HashSet<>();
    private final Map<String, Group> groups;

    private final List<Group> global;

    private final MultiMap<String, Group> byPackage;
    private final MultiMap<String, Group> byClass;

    private final Map<PsiMethod, List<Group>> methodScopes;

    // This list only exists to keep the base elements in memory and prevent them from being GC'd and therefore losing their user data
    // JavaPsiFacade#findClass uses a soft key and soft value map
    @SuppressWarnings({"FieldCanBeLocal", "MismatchedQueryAndUpdateOfCollection"})
    private final List<PsiElement> baseElements;

    public UnpickCollection(TransformContext context, Map<TypedKey, GroupDefinition> groups, List<TargetField> fields, List<TargetMethod> methods) {
        this.groups = new HashMap<>(groups.size());
        groups.forEach((k, v) -> this.groups.put(k.name(), Group.create(v)));

        var facade = context.environment().getPsiFacade();
        var project = context.environment().getPsiManager().getProject();

        var projectScope = GlobalSearchScope.projectScope(project);

        global = new ArrayList<>();

        byPackage = new MultiMap<>();
        byClass = new MultiMap<>();

        methodScopes = new IdentityHashMap<>();
        baseElements = new ArrayList<>();

        groups.forEach((s, def) -> {
            var gr = Group.create(def);
            if (def.scope instanceof GroupScope.Package pkg) {
                byPackage.putValue(pkg.packageName, gr);
            } else if (def.scope instanceof GroupScope.Class cls) {
                byClass.putValue(cls.className, gr);
            } else if (def.scope instanceof GroupScope.Global && def.name == null) {
                global.add(gr);
            } else if (def.scope instanceof GroupScope.Method mtd) {
                var cls = facade.findClass(mtd.className, projectScope);
                if (cls == null) return;

                for (PsiMethod clsMethod : cls.getMethods()) {
                    if (clsMethod.getName().equals(mtd.methodName) && PsiHelper.getBinaryMethodSignature(clsMethod).equals(mtd.methodDesc)) {
                        methodScopes.computeIfAbsent(clsMethod, k -> new ArrayList<>()).add(gr);
                    }
                }
            }
        });

        for (var field : fields) {
            var cls = facade.findClass(field.className, projectScope);
            if (cls == null) continue;

            var fld = cls.findFieldByName(field.fieldName, true);
            if (fld != null) {
                fld.putUserData(UNPICK_FIELD_TARGET, field);
                baseElements.add(fld);
            }
        }

        for (var method : methods) {
            var cls = facade.findClass(method.className, projectScope);
            if (cls == null) continue;

            possibleTargetNames.add(method.methodName);

            for (PsiMethod clsMethod : cls.getMethods()) {
                if (clsMethod.getName().equals(method.methodName) && PsiHelper.getBinaryMethodSignature(clsMethod).equals(method.methodDesc)) {
                    clsMethod.putUserData(UNPICK_DEFINITION, Optional.of(method));
                    baseElements.add(clsMethod);
                }
            }
        }
    }

    public boolean forEachInScope(PsiClass cls, @Nullable PsiMethod scope, Predicate<Group> pred) {
        if (scope != null) {
            var metScoped = methodScopes.get(scope);
            if (metScoped != null) {
                for (Group group : metScoped) {
                    if (pred.test(group)) return true;
                }
            }
        }

        var clsName = cls.getQualifiedName();
        if (clsName != null) {
            for (Group group : byClass.get(clsName)) {
                if (pred.test(group)) {
                    return true;
                }
            }
        }

        var par = cls.getParent();
        while (par != null && !(par instanceof PsiJavaFile)) {
            par = par.getParent();
        }

        if (par instanceof PsiJavaFile file) {
            for (Group group : byPackage.get(file.getPackageName())) {
                if (pred.test(group)) {
                    return true;
                }
            }
        }

        for (Group group : global) {
            if (pred.test(group)) {
                return true;
            }
        }

        return false;
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

    @Nullable
    public Group getGroup(String id) {
        return groups.get(id);
    }

    public record Group(
            DataType data,
            boolean strict,
            @Nullable GroupFormat format,
            GroupType type,
            Map<Object, Expression> constants
    ) {
        public static Group create(GroupDefinition def) {
            return new Group(
                    def.dataType,
                    def.strict,
                    def.format,
                    def.type,
                    def.constants.stream()
                            .collect(Collectors.toMap(
                                    g -> getKey(g.key, def.dataType),
                                    g -> g.value
                            ))
            );
        }

        private static Object getKey(Literal.ConstantKey key, DataType type) {
            if (key instanceof Literal.NumberConstant nct) {
                var val = nct.asNumber();
                return switch (type) {
                    case CHAR -> (char)val.intValue();
                    case BYTE -> val.byteValue();
                    case SHORT -> val.shortValue();
                    case INT -> val.intValue();
                    case LONG -> val.longValue();
                    case FLOAT -> val.floatValue();
                    case DOUBLE -> val.doubleValue();
                    case STRING -> throw null;
                };
            }
            if (key.getClass() == Literal.String.class) {
                return ((Literal.String) key).value;
            }
            return null;
        }
    }

    public record TypedKey(DataType type, GroupScope scope, @Nullable String name) {

    }
}
