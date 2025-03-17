package net.earthcomputer.unpickv3parser.tree;

import net.earthcomputer.unpickv3parser.tree.expr.Expression;

public final class GroupConstant {
    public final Literal.ConstantKey key;
    public final Expression value;

    public GroupConstant(Literal.ConstantKey key, Expression value) {
        this.key = key;
        this.value = value;
    }
}
