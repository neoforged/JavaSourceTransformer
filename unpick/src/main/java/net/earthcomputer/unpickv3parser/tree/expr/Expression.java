package net.earthcomputer.unpickv3parser.tree.expr;

public abstract class Expression {
    Expression() {
    }

    public abstract void accept(ExpressionVisitor visitor);

    public abstract Expression transform(ExpressionTransformer transformer);
}
