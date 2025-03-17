package net.earthcomputer.unpickv3parser.tree.expr;

import net.earthcomputer.unpickv3parser.tree.Literal;

public final class LiteralExpression extends Expression {
    /**
     * Note: this literal is always positive. Integers are to be interpreted as unsigned values.
     */
    public final Literal literal;

    public LiteralExpression(Literal literal) {
        this.literal = literal;
    }

    @Override
    public void accept(ExpressionVisitor visitor) {
        visitor.visitLiteralExpression(this);
    }

    @Override
    public Expression transform(ExpressionTransformer transformer) {
        return transformer.transformLiteralExpression(this);
    }
}
