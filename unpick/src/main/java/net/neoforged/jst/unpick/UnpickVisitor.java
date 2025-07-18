package net.neoforged.jst.unpick;

import com.intellij.openapi.util.Key;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiAssignmentExpression;
import com.intellij.psi.PsiCallExpression;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiPrefixExpression;
import com.intellij.psi.PsiRecursiveElementVisitor;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiReturnStatement;
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
import java.util.List;
import java.util.Locale;
import java.util.Objects;
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

    private PsiClass classContext;

    @Nullable
    private PsiMethod methodContext;
    @Nullable
    private PsiField fieldContext;

    @Nullable
    private PsiMethod calledMethod;
    @Nullable
    private TargetMethod methodCallTarget;

    private int currentParamIndex;
    private boolean withinReturnStatement;

    @Override
    public void visitElement(@NotNull PsiElement element) {
        if (element instanceof PsiClass cls) {
            var oldCls = classContext;
            this.classContext = cls;
            cls.acceptChildren(this);
            this.classContext = oldCls;
            return;
        } else if (element instanceof PsiMethod met) {
            if (met.getBody() != null) {
                var oldCtx = this.methodContext;
                this.methodContext = met;
                var oldInReturn = this.withinReturnStatement;
                this.withinReturnStatement = false;

                met.getBody().acceptChildren(this);
                this.methodContext = oldCtx;
                this.withinReturnStatement = oldInReturn;
            }
            return;
        } else if (element instanceof PsiField fld) {
            var oldCtx = this.fieldContext;
            this.fieldContext = fld;
            fld.acceptChildren(this);
            this.fieldContext = oldCtx;
            return;
        } else if (element instanceof PsiJavaToken tok) {
            visitToken(tok);
            return;
        } else if (element instanceof PsiMethodCallExpression call) {
            PsiElement ref = PsiHelper.resolve(call.getMethodExpression());
            if (ref instanceof PsiMethod met) {
                var oldMet = calledMethod;
                calledMethod = met;
                methodCallTarget = null;

                for (int i = 0; i < call.getArgumentList().getExpressions().length; i++) {
                    var oldIndex = currentParamIndex;
                    currentParamIndex = i;

                    // If any parameter of the method call is directly referencing local var we re-walk the entire method body
                    // and apply unpick with the context of the method being called to all of its assignments (including the initialiser)
                    acceptPossibleLocalVarReference(call.getArgumentList().getExpressions()[i]);

                    currentParamIndex = oldIndex;
                }
                calledMethod = oldMet;
            }
            return;
        } else if (element instanceof PsiReturnStatement returnStatement) {
            this.withinReturnStatement = true;
            acceptPossibleLocalVarReference(returnStatement.getReturnValue());
            this.withinReturnStatement = false;
        }

        element.acceptChildren(this);
    }

    private void acceptPossibleLocalVarReference(PsiExpression expression) {
        if (expression instanceof PsiReferenceExpression refEx && methodContext != null) {
            PsiCodeBlock body = methodContext.getBody();
            PsiElement resolved = PsiHelper.resolve(refEx);

            if (body != null && resolved instanceof PsiLocalVariable localVar) {
                if (localVar.getInitializer() != null) {
                    localVar.getInitializer().accept(limitedDirectVisitor());
                }

                new PsiRecursiveElementVisitor() {
                    @Override
                    public void visitElement(@NotNull PsiElement element) {
                        if (element instanceof PsiAssignmentExpression as) {
                            if (as.getOperationSign().getTokenType() == JavaTokenType.EQ && as.getLExpression() instanceof PsiReferenceExpression ref && PsiHelper.resolve(ref) == localVar && as.getRExpression() != null) {
                                as.getRExpression().accept(limitedDirectVisitor());
                            }
                            return;
                        }
                        super.visitElement(element);
                    }
                }.visitElement(body);
                return;
            }
        }

        if (expression != null) {
            visitElement(expression);
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
            var val = tok.getText().substring(1); // Remove starting "
            final var finalVal = val.substring(0, val.length() - 1); // Remove leading "
            forInScope(group -> {
                var ct = group.constants().get(finalVal);
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
                replacements.replace(element, write(ct));
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
                if (lower.doubleValue() == number.doubleValue()) {
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
        if (fieldContext != null && expression instanceof FieldExpression fld) {
            return !(fld.className.equals(classContext.getQualifiedName()) && Objects.equals(fld.fieldName, fieldContext.getName()));
        }
        return true;
    }

    private boolean forInScope(Predicate<UnpickCollection.Group> apply) {
        if (calledMethod != null) {
            if (methodCallTarget == null) {
                methodCallTarget = collection.getDefinitionsFor(calledMethod);
            }
            if (methodCallTarget != null) {
                var groupId = methodCallTarget.paramGroups().get(currentParamIndex);
                if (groupId != null) {
                    var groups = collection.getGroups(groupId);
                    if (!groups.isEmpty()) {
                        for (var group : groups) {
                            if (apply.test(group)) {
                                return true;
                            }
                        }
                    }
                }
            }
        }

        if (withinReturnStatement && methodContext != null) {
            var contextDefinitions = collection.getDefinitionsFor(methodContext);
            if (contextDefinitions != null && contextDefinitions.returnGroup() != null) {
                var groups = collection.getGroups(contextDefinitions.returnGroup());
                if (!groups.isEmpty()) {
                    for (var group : groups) {
                        if (apply.test(group)) {
                            return true;
                        }
                    }
                }
            }
        }

        return collection.forEachInScope(classContext, methodContext, apply);
    }

    private String write(Expression expression) {
        StringBuilder s = new StringBuilder();
        expression.accept(new ExpressionVisitor() {
            @Override
            public void visitFieldExpression(FieldExpression fieldExpression) {
                var cls = imports().importClass(fieldExpression.className);
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
                    s.append('\"').append(value.replace("\"", "\\\"")).append('\"');
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

            default -> value.toString();
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

        StringBuilder replacement = new StringBuilder(write(constants.getFirst()));
        for (int i = 1; i < constants.size(); i++) {
            replacement.append(" | ");
            replacement.append(write(constants.get(i)));
        }

        if (residual != 0) {
            replacement.append(" | ").append(residual);
        }

        if (negated) {
            return "~" + replacement;
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
