package net.neoforged.jst.unpick;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiAssignmentExpression;
import com.intellij.psi.PsiCallExpression;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiPrefixExpression;
import com.intellij.psi.PsiRecursiveElementVisitor;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiReturnStatement;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.util.ClassUtil;
import daomephsta.unpick.constantmappers.datadriven.tree.GroupFormat;
import daomephsta.unpick.constantmappers.datadriven.tree.Literal;
import daomephsta.unpick.constantmappers.datadriven.tree.TargetMethod;
import daomephsta.unpick.constantmappers.datadriven.tree.expr.BinaryExpression;
import daomephsta.unpick.constantmappers.datadriven.tree.expr.CastExpression;
import daomephsta.unpick.constantmappers.datadriven.tree.expr.Expression;
import daomephsta.unpick.constantmappers.datadriven.tree.expr.ExpressionVisitor;
import daomephsta.unpick.constantmappers.datadriven.tree.expr.FieldExpression;
import daomephsta.unpick.constantmappers.datadriven.tree.expr.LiteralExpression;
import daomephsta.unpick.constantmappers.datadriven.tree.expr.ParenExpression;
import daomephsta.unpick.constantmappers.datadriven.tree.expr.UnaryExpression;
import net.neoforged.jst.api.ImportHelper;
import net.neoforged.jst.api.PsiHelper;
import net.neoforged.jst.api.Replacements;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Stack;
import java.util.function.Predicate;

public class UnpickVisitor extends PsiRecursiveElementVisitor {
    private static final Key<Boolean> UNPICK_WAS_REPLACED = Key.create("unpick.was_replaced");

    private final PsiFile file;
    private final UnpickCollection collection;
    private final Replacements replacements;

    public UnpickVisitor(PsiFile file, UnpickCollection collection, Replacements replacements) {
        this.file = file;
        this.collection = collection;
        this.replacements = replacements;
    }

    @Nullable
    private PsiMethod methodContext;
    @Nullable
    private PsiField fieldContext;

    @Nullable
    private TargetMethod calledMethodContext;
    private int currentParameterIndex;

    private final Stack<Collection<UnpickCollection.Group>> contextStack = new Stack<>();

    @Override
    public void visitElement(@NotNull PsiElement element) {
        Collection<UnpickCollection.Group> additionalContext = List.of();

        switch (element) {
            case PsiJavaFile javaFile -> additionalContext = collection.getPackageContext(javaFile);
            case PsiClass cls -> additionalContext = collection.getClassContext(cls);
            case PsiMethod met when met.getBody() != null -> {
                var oldCtx = this.methodContext;
                contextStack.push(collection.getMethodContext(met));
                this.methodContext = met;
                met.getBody().acceptChildren(this);
                this.methodContext = oldCtx;
                contextStack.pop();
                return;
            }
            case PsiField fld -> {
                var oldCtx = this.fieldContext;
                this.fieldContext = fld;
                fld.acceptChildren(this);
                this.fieldContext = oldCtx;
                return;
            }

            case PsiJavaToken tok -> {
                visitToken(tok);
                return;
            }

            case PsiMethodCallExpression call -> {
                PsiElement ref = PsiHelper.resolve(call.getMethodExpression());
                if (ref instanceof PsiMethod met) {
                    var oldCtx = this.calledMethodContext;
                    var oldIdx = this.currentParameterIndex;

                    // We replace the old context to avoid nesting as it can produce weird artifacts
                    // when parameter expression are themselves other method calls since "invasive"
                    // (i.e. number formatting) rules of this group would apply to its parameters
                    this.calledMethodContext = collection.getDefinitionsFor(met);
                    for (int i = 0; i < call.getArgumentList().getExpressions().length; i++) {
                        this.currentParameterIndex = i;
                        // If any parameter of the method call is directly referencing local var we re-walk the entire method body
                        // and apply unpick with the context of the method being called to all of its assignments (including the initialiser)
                        acceptPossibleLocalVarReference(call.getArgumentList().getExpressions()[i]);
                    }

                    this.calledMethodContext = oldCtx;
                    this.currentParameterIndex = oldIdx;
                    return;
                }
            }
            case PsiReturnStatement returnStatement when methodContext != null -> {
                var contextDefinitions = collection.getDefinitionsFor(methodContext);
                if (contextDefinitions != null && contextDefinitions.returnGroup() != null) {
                    var groups = collection.getGroups(contextDefinitions.returnGroup());
                    if (!groups.isEmpty()) {
                        contextStack.push(groups);
                        acceptPossibleLocalVarReference(returnStatement.getReturnValue());
                        contextStack.pop();
                        return;
                    }
                }
            }

            case PsiLocalVariable localVar
                    when localVar.getInitializer() != null && localVar.getInitializer() instanceof PsiMethodCallExpression methodCall -> {
                acceptReturnFlow(localVar, methodCall);
                return;
            }

            case PsiAssignmentExpression assignment -> {
                if (assignment.getOperationSign().getTokenType() == JavaTokenType.EQ && assignment.getLExpression() instanceof PsiReferenceExpression ref) {
                    var referencedVariable = PsiHelper.resolve(ref);
                    if (referencedVariable instanceof PsiLocalVariable || referencedVariable instanceof PsiParameter) {
                        if (assignment.getLExpression() instanceof PsiMethodCallExpression methodCall) {
                            acceptReturnFlow((PsiVariable) referencedVariable, methodCall);
                            return;
                        }
                    }
                }
            }

            default -> {}
        }

        if (additionalContext.isEmpty()) {
            element.acceptChildren(this);
        } else {
            contextStack.push(additionalContext);
            element.acceptChildren(this);
            contextStack.pop();
        }
    }

    private void acceptReturnFlow(PsiVariable variable, PsiMethodCallExpression expression) {
        var flowingFrom = expression.resolveMethod();
        if (flowingFrom != null) {
            var target = collection.getDefinitionsFor(flowingFrom);
            if (target != null && target.returnGroup() != null) {
                var groups = collection.getGroups(target.returnGroup());
                if (!groups.isEmpty()) {
                    contextStack.push(groups);
                    visitVariableAssignments(variable);
                    contextStack.pop();
                    return;
                }
            }
        }
        expression.acceptChildren(this);
    }

    private void acceptPossibleLocalVarReference(PsiExpression expression) {
        if (expression instanceof PsiReferenceExpression refEx) {
            PsiElement resolved = PsiHelper.resolve(refEx);

            if (resolved instanceof PsiLocalVariable || resolved instanceof PsiParameter) {
                visitVariableAssignments((PsiVariable) resolved);
                return;
            }
        }

        if (expression != null) {
            visitElement(expression);
        }
    }

    private void visitVariableAssignments(PsiVariable var) {
        if (var.getInitializer() != null) {
            var.getInitializer().accept(limitedDirectVisitor());
        }

        var body = methodContext == null ? null : methodContext.getBody();
        if (body != null) {
            new PsiRecursiveElementVisitor() {
                @Override
                public void visitElement(@NotNull PsiElement element) {
                    if (element instanceof PsiAssignmentExpression as) {
                        if (as.getOperationSign().getTokenType() == JavaTokenType.EQ && as.getLExpression() instanceof PsiReferenceExpression ref && PsiHelper.resolve(ref) == var && as.getRExpression() != null) {
                            as.getRExpression().accept(limitedDirectVisitor());
                        }
                        return;
                    }
                    super.visitElement(element);
                }
            }.visitElement(body);
        }
    }

    /**
     * {@return an element visitor that visits only tokens outside of call expressions}
     * This can be used when there is no need to handle call expressions as they would have already
     * been handled or will be handled - for instance, when re-applying unpick for local variable initialisers,
     * but with more context.
     */
    private PsiRecursiveElementVisitor limitedDirectVisitor() {
        return new PsiRecursiveElementVisitor() {
            @Override
            public void visitElement(@NotNull PsiElement element) {
                if (element instanceof PsiCallExpression) {
                    return; // We do not want to try to further replace constants inside method calls, that's why we're limited to direct elements
                }
                if (element instanceof PsiJavaToken tok) {
                    visitToken(tok);
                    return;
                }
                super.visitElement(element);
            }
        };
    }

    private void visitToken(PsiJavaToken tok) {
        if (Boolean.TRUE.equals(tok.getUserData(UNPICK_WAS_REPLACED))) return;

        if (tok.getTokenType() == JavaTokenType.STRING_LITERAL) {
            // Remove starting and leading " and unescape characters to turn from the source representation to the runtime one
            final String value  = StringUtil.unescapeStringCharacters(StringUtil.unquoteString(tok.getText()));
            forInScope(group -> {
                var ct = group.constants().get(value);
                if (ct != null && checkNotRecursive(ct)) {
                    replacements.replace(tok, write(ct));
                    tok.putUserData(UNPICK_WAS_REPLACED, true);
                    return true;
                }
                return false;
            });
        } else if (tok.getTokenType() == JavaTokenType.INTEGER_LITERAL) {
            var text = tok.getText().toLowerCase(Locale.ROOT);

            int val;
            if (text.startsWith("0x")) {
                val = Integer.parseUnsignedInt(text.substring(2), 16);
            } else if (text.startsWith("0b")) {
                val = Integer.parseUnsignedInt(text.substring(2), 2);
            } else {
                val = Integer.parseUnsignedInt(text);
            }

            if (isUnaryMinus(tok)) val = -val;
            replaceLiteral(tok, val, NumberType.INT);
        } else if (tok.getTokenType() == JavaTokenType.LONG_LITERAL) {
            var text = removeSuffix(tok.getText(), 'l').toLowerCase(Locale.ROOT);

            long val;
            if (text.startsWith("0x")) {
                val = Long.parseUnsignedLong(text.substring(2), 16);
            } else if (text.startsWith("0b")) {
                val = Long.parseUnsignedLong(text.substring(2), 2);
            } else {
                val = Long.parseUnsignedLong(text);
            }

            if (isUnaryMinus(tok)) val = -val;
            replaceLiteral(tok, val, NumberType.LONG);
        } else if (tok.getTokenType() == JavaTokenType.DOUBLE_LITERAL) {
            var val = Double.parseDouble(removeSuffix(tok.getText(), 'd'));
            if (isUnaryMinus(tok)) val = -val;
            replaceLiteral(tok, val, NumberType.DOUBLE);
        } else if (tok.getTokenType() == JavaTokenType.FLOAT_LITERAL) {
            var val = Float.parseFloat(removeSuffix(tok.getText(), 'f'));
            if (isUnaryMinus(tok)) val = -val;
            replaceLiteral(tok, val, NumberType.FLOAT);
        }
    }

    private void replaceLiteral(PsiJavaToken element, Number number, NumberType type) {
        replaceLiteral(element, number, type, false);
    }

    private boolean replaceLiteral(PsiJavaToken element, Number number, NumberType type, boolean denyStrict) {
        return forInScope(group -> {
            // If we need to deny strict conversion (so if this is a conversion) we shall do so
            if (group.strict() && denyStrict) return false;

            // We'll try a direct constant first, even if it's a flag
            var ct = group.constants().get(number);
            if (ct != null && checkNotRecursive(ct)) {
                replacements.replace(element, writeOptionallySurround(ct));
                element.putUserData(UNPICK_WAS_REPLACED, true);
                replaceMinus(element);
                return true;
            }

            // Next, try if this group is a flag and the number type supports flags (ints and longs) we try to generate the flag combination
            if (group.flag() && type.supportsFlag) {
                // We generate flags for ints based on their long value as
                // longs are a superset of ints and as such we can reduce code duplication
                var flag = generateFlag(group, number.longValue(), type);
                if (flag != null) {
                    replacements.replace(element, flag);
                    element.putUserData(UNPICK_WAS_REPLACED, true);
                    replaceMinus(element);
                    return true;
                }
            }

            // As a fallback, if the group has a specific format but the
            // value of the token does not have a constant we format the token
            if (group.format() != null) {
                replacements.replace(element, formatAs(number, group.format()));
                replaceMinus(element);
                element.putUserData(UNPICK_WAS_REPLACED, true);
                return true;
            }

            // Finally we try to apply non-strict widening from lower number types
            for (NumberType from : type.widenFrom) {
                var lower = from.cast(number);
                if (lower.doubleValue() == number.doubleValue() && lower.longValue() == number.longValue()) {
                    if (replaceLiteral(element, lower, from, true)) {
                        return true;
                    }
                }
            }

            return false;
        });
    }

    private boolean isUnaryMinus(PsiJavaToken tok) {
        return tok.getParent() instanceof PsiLiteralExpression lit && lit.getParent() instanceof PsiPrefixExpression ex && ex.getOperationTokenType() == JavaTokenType.MINUS;
    }

    private void replaceMinus(PsiJavaToken tok) {
        if (tok.getParent() instanceof PsiLiteralExpression lit && lit.getParent() instanceof PsiPrefixExpression ex && ex.getOperationTokenType() == JavaTokenType.MINUS) {
            replacements.remove(ex.getOperationSign());
        }
    }

    private boolean checkNotRecursive(Expression expression) {
        if (fieldContext != null && fieldContext.getContainingClass() != null && expression instanceof FieldExpression fld) {
            return !(fld.className.equals(ClassUtil.getJVMClassName(fieldContext.getContainingClass())) && Objects.equals(fld.fieldName, fieldContext.getName()));
        }
        return true;
    }

    private boolean forInScope(Predicate<UnpickCollection.Group> apply) {
        if (calledMethodContext != null) {
            var paramGroupId = this.calledMethodContext.paramGroups().get(currentParameterIndex);
            if (paramGroupId != null) {
                var paramGroups = collection.getGroups(paramGroupId);
                if (!paramGroups.isEmpty()) {
                    for (var group : paramGroups) {
                        if (apply.test(group)) {
                            return true;
                        }
                    }
                }
            }
        }

        if (!contextStack.isEmpty()) {
            // Walk and apply the context stack in reverse (e.g. we first apply the method scope, then the class scope and finally the package scope)
            for (int i = contextStack.size() - 1; i >= 0; i--) {
                for (var group : contextStack.get(i)) {
                    if (apply.test(group)) {
                        return true;
                    }
                }
            }
        }

        for (var group : collection.getGlobalContext()) {
            if (apply.test(group)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Write the given expression, optionally surrounding it with parenthesis if
     * it cannot be safely assumed not to need them.
     * <p>
     * Only literals or top-level fields will not be surrounded.
     */
    private String writeOptionallySurround(Expression expression) {
        return (expression instanceof LiteralExpression || expression instanceof FieldExpression) ? write(expression) : "(" + write(expression) + ")";
    }

    private String write(Expression expression) {
        StringBuilder s = new StringBuilder();
        expression.accept(new ExpressionVisitor() {
            @Override
            public void visitFieldExpression(FieldExpression fieldExpression) {
                var cls = imports().importClass(fieldExpression.className.replace("$", "."));
                s.append(cls).append('.').append(fieldExpression.fieldName);
            }

            @Override
            public void visitParenExpression(ParenExpression parenExpression) {
                s.append('(')
                        .append(write(parenExpression.expression))
                        .append(')');
            }

            @Override
            public void visitLiteralExpression(LiteralExpression literalExpression) {
                if (literalExpression.literal instanceof Literal.String(String value)) {
                    s.append('\"').append(StringUtil.escapeStringCharacters(value)).append('\"');
                } else if (literalExpression.literal instanceof Literal.Integer i) {
                    s.append(i.value());
                } else if (literalExpression.literal instanceof Literal.Long l) {
                    s.append(l.value()).append('l');
                } else if (literalExpression.literal instanceof Literal.Double d) {
                    s.append(d).append('d');
                } else if (literalExpression.literal instanceof Literal.Float f) {
                    s.append(f).append('f');
                }
            }

            @Override
            public void visitCastExpression(CastExpression castExpression) {
                s.append('(');
                s.append(switch (castExpression.castType) {
                    case INT -> "int";
                    case CHAR -> "char";
                    case DOUBLE -> "double";
                    case BYTE -> "byte";
                    case LONG -> "long";
                    case FLOAT -> "float";
                    case SHORT -> "short";
                    case STRING -> "String";
                    case CLASS -> "Class";
                });
                s.append(')');
                s.append(write(castExpression.operand));
            }

            @Override
            public void visitBinaryExpression(BinaryExpression binaryExpression) {
                s.append(write(binaryExpression.lhs));
                switch (binaryExpression.operator) {
                    case ADD -> s.append(" + ");
                    case DIVIDE -> s.append(" / ");
                    case MODULO -> s.append(" % ");
                    case MULTIPLY -> s.append(" * ");
                    case SUBTRACT -> s.append(" - ");

                    case BIT_AND -> s.append(" & ");
                    case BIT_OR -> s.append(" | ");
                    case BIT_XOR -> s.append(" ^ ");

                    case BIT_SHIFT_LEFT -> s.append(" << ");
                    case BIT_SHIFT_RIGHT -> s.append(" >> ");
                    case BIT_SHIFT_RIGHT_UNSIGNED -> s.append(" >>> ");
                }
                s.append(write(binaryExpression.rhs));
            }

            @Override
            public void visitUnaryExpression(UnaryExpression unaryExpression) {
                switch (unaryExpression.operator) {
                    case NEGATE -> s.append("!");
                    case BIT_NOT -> s.append("~");
                }
                s.append(write(unaryExpression.operand));
            }
        });
        return s.toString();
    }

    private String formatAs(Number value, GroupFormat format) {
        return switch (format) {
            case HEX -> {
                if (value instanceof Integer) yield "0x" + Integer.toHexString(value.intValue()).toUpperCase(Locale.ROOT);
                else if (value instanceof Long) yield "0x" + Long.toHexString(value.longValue()).toUpperCase(Locale.ROOT) + "l";
                else if (value instanceof Double) yield Double.toHexString(value.doubleValue()) + "d";
                else if (value instanceof Float) yield Float.toHexString(value.floatValue()) + "f";
                yield value.toString();
            }
            case OCTAL -> {
                if (value instanceof Integer) yield "0" + Integer.toOctalString(value.intValue());
                else if (value instanceof Long) yield "0" + Long.toOctalString(value.longValue()) + "l";
                yield value.toString();
            }
            case BINARY -> {
                if (value instanceof Integer) yield "0b" + Integer.toBinaryString(value.intValue());
                else if (value instanceof Long) yield "0b" + Long.toBinaryString(value.longValue()) + "l";
                yield value.toString();
            }
            case CHAR -> "'" + ((char) value.intValue()) + "'";

            default -> switch (value) {
                case Long l -> l + "l";
                case Float f -> f + "f";
                default -> value.toString();
            };
        };
    }

    private ImportHelper imports() {
        return ImportHelper.get(file);
    }

    @Nullable
    private String generateFlag(UnpickCollection.Group group, long val, NumberType type) {
        List<Expression> orConstants = new ArrayList<>();
        long orResidual = getConstantsEncompassing(val, type, group, orConstants);
        long negatedLiteral = type.toUnsignedLong(type.negate(val));
        List<Expression> negatedConstants = new ArrayList<>();
        long negatedResidual = getConstantsEncompassing(negatedLiteral, type, group, negatedConstants);

        boolean negated = negatedResidual == 0 && (orResidual != 0 || negatedConstants.size() < orConstants.size());
        List<Expression> constants = negated ? negatedConstants : orConstants;
        if (constants.isEmpty())
            return null;

        long residual = negated ? negatedResidual : orResidual;

        boolean paren = false;

        StringBuilder replacement = new StringBuilder(writeOptionallySurround(constants.getFirst()));
        for (int i = 1; i < constants.size(); i++) {
            replacement.append(" | ");
            replacement.append(writeOptionallySurround(constants.get(i)));
            paren = true;
        }

        if (residual != 0) {
            boolean isLong = residual < Integer.MIN_VALUE || residual > Integer.MAX_VALUE;

            replacement.append(" | ");

            // The formatAs method appends l automatically to any long value
            // so if it's an int we downcast it to avoid it
            replacement.append(formatAs(isLong ? (Number) residual : (Number) (int) residual, Objects.requireNonNullElse(group.format(), GroupFormat.DECIMAL)));

            paren = true;
        }

        if (negated) {
            return "~" + (paren ? ("(" + replacement + ")") : replacement);
        }

        return replacement.toString();
    }

    /**
     * Adds the constants that encompass {@code literal} to {@code constantsOut}.
     * Returns the residual (bits set in the literal not covered by the returned constants).
     */
    private static long getConstantsEncompassing(long literal, NumberType unsign, UnpickCollection.Group group, List<Expression> constantsOut) {
        long residual = literal;
        for (var constant : group.constants().entrySet()) {
            long val = unsign.toUnsignedLong((Number) constant.getKey());
            if ((val & residual) != 0 && (val & literal) == val) {
                residual &= ~val;
                constantsOut.add(constant.getValue());
                if (residual == 0)
                    break;
            }
        }
        return residual;
    }

    private static String removeSuffix(String in, char suffix) {
        var lastChar = in.charAt(in.length() - 1);
        if (lastChar == suffix || lastChar == Character.toUpperCase(suffix)) {
            return in.substring(0, in.length() - 1);
        }
        return in;
    }
}
